// Реальный запуск Fabric + Laura Client.
// JAR клиента устанавливается как Fabric-мод в папку instance/mods,
// а Minecraft и зависимости подготавливаются в отдельном инстансе.
const { spawn, spawnSync } = require('child_process');
const path = require('path');
const fs = require('fs');
const os = require('os');
const crypto = require('crypto');
const http = require('http');
const https = require('https');
const zlib = require('zlib');

const FABRIC_META_URL = 'https://meta.fabricmc.net/v2/versions/loader';
const MINECRAFT_MANIFEST_URL = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json';
const MODRINTH_API_URL = 'https://api.modrinth.com/v2/project/fabric-api/version';
const DEFAULT_FABRIC_LOADER = '0.18.4';
const DEFAULT_VERSION = '1.21.4';
// Версия Fabric API, под которую собран Laura Client (см. gradle.properties).
const PREFERRED_FABRIC_API_VERSION = '0.119.4';
const CLIENT_JAR_PATTERN = /^laura-client(?:-[^/]*)?\.jar$/i;
const FABRIC_API_JAR_PATTERN = /^fabric-api-[^/\\]+\.jar$/i;

const PLATFORM_OS = {
    win32: 'windows',
    darwin: 'osx',
    linux: 'linux'
};

class MinecraftLauncher {
    constructor(config) {
        // Принимаем как Config-инстанс (с get/set), так и plain-объект данных.
        // Раньше сюда всегда передавали plain-объект, а в launch() вызывались
        // несуществующие this.config.data / this.config.save() — запуск игры
        // всегда заканчивался ошибкой даже при успешном старте процесса.
        if (config && typeof config.get === 'function' && typeof config.set === 'function') {
            this.configStore = config;
            this.config = config.get();
        } else {
            this.configStore = null;
            this.config = config;
        }
    }

    // Единый резолв пути инстанса: используется и запуском игры,
    // и кнопкой «Папка игры», чтобы открывалась та же папка.
    static resolveInstancePath(configData) {
        const baseDirectory = process.env.LOCALAPPDATA || process.env.APPDATA || os.homedir() || process.cwd();
        const configured = configData && configData.game && configData.game.instancePath;
        const trimmed = typeof configured === 'string' ? configured.trim() : '';
        return path.resolve(trimmed || path.join(baseDirectory, 'Laura Client', 'instance'));
    }

    getInstancePath() {
        return MinecraftLauncher.resolveInstancePath(this.config);
    }

    // Сообщение о этапе подготовки в интерфейс (см. onProgress в main.js).
    report(message) {
        try {
            if (typeof this.onProgress === 'function') this.onProgress(message);
        } catch (_) { /* прогресс не должен ломать запуск */ }
    }

    // Получить список релизов Minecraft. Оставлено для совместимости IPC API.
    static async fetchVersions(type = 'release') {
        const manifest = await MinecraftLauncher.getJson(MINECRAFT_MANIFEST_URL);
        return manifest.versions
            .filter(version => version.type === type)
            .slice(0, 50)
            .map(version => ({ id: version.id, type: version.type, url: version.url }));
    }

    static request(url, redirects = 0) {
        if (redirects > 5) {
            return Promise.reject(new Error('Слишком много перенаправлений при загрузке файлов.'));
        }

        return new Promise((resolve, reject) => {
            let parsed;
            try {
                parsed = new URL(url);
            } catch (error) {
                reject(new Error(`Некорректный URL: ${url}`));
                return;
            }

            const transport = parsed.protocol === 'http:' ? http : https;
            const request = transport.get(parsed, {
                headers: { 'User-Agent': 'Laura-Launcher/1.0' }
            }, response => {
                const status = response.statusCode || 0;
                if ([301, 302, 303, 307, 308].includes(status) && response.headers.location) {
                    response.resume();
                    const redirected = new URL(response.headers.location, parsed).toString();
                    MinecraftLauncher.request(redirected, redirects + 1)
                        .then(resolve)
                        .catch(reject);
                    return;
                }
                if (status < 200 || status >= 300) {
                    response.resume();
                    reject(new Error(`Сервер вернул HTTP ${status} для ${url}`));
                    return;
                }
                resolve(response);
            });

            request.setTimeout(60_000, () => {
                request.destroy(new Error(`Истекло время ожидания: ${url}`));
            });
            request.on('error', reject);
        });
    }

    static async getJson(url) {
        const response = await MinecraftLauncher.request(url);
        const chunks = [];
        for await (const chunk of response) chunks.push(chunk);
        try {
            return JSON.parse(Buffer.concat(chunks).toString('utf8'));
        } catch (error) {
            throw new Error(`Не удалось разобрать ответ сервера: ${url}`);
        }
    }

    static async downloadFile(url, destination) {
        if (fs.existsSync(destination)) return destination;
        fs.mkdirSync(path.dirname(destination), { recursive: true });
        const temporary = `${destination}.part`;
        try {
            if (fs.existsSync(temporary)) fs.rmSync(temporary, { force: true });
            const response = await MinecraftLauncher.request(url);
            await new Promise((resolve, reject) => {
                const file = fs.createWriteStream(temporary);
                response.pipe(file);
                response.on('error', reject);
                file.on('error', reject);
                file.on('finish', () => file.close(resolve));
            });
            fs.renameSync(temporary, destination);
            return destination;
        } catch (error) {
            try { fs.rmSync(temporary, { force: true }); } catch (_) { /* best effort */ }
            throw new Error(`Не удалось скачать ${url}: ${error.message}`);
        }
    }

    // Проверяет, что Java существует и её версия подходит для 1.21.4 (21+).
    // Возвращает { ok, version } — вместо молчаливого запуска не той Java,
    // после которого игра мгновенно закрывается без понятной ошибки.
    static checkJavaVersion(javaPath) {
        try {
            const result = spawnSync(javaPath, ['-version'], {
                encoding: 'utf8',
                timeout: 10_000,
                windowsHide: true
            });
            if (result.error || result.status !== 0) return { ok: false, version: null };
            const output = `${result.stdout || ''}\n${result.stderr || ''}`;
            const match = output.match(/version\s+"(\d+)(?:\.(\d+))?/);
            if (!match) return { ok: false, version: null };
            // Старая нумерация 1.8 -> 8, новая 21.x -> 21.
            const major = match[1] === '1' && match[2] ? parseInt(match[2], 10) : parseInt(match[1], 10);
            return { ok: Number.isFinite(major) && major >= 21, version: major };
        } catch (_) {
            return { ok: false, version: null };
        }
    }

    findJava() {
        const configured = this.config.game.javaPath;
        const exe = process.platform === 'win32' ? 'java.exe' : 'java';
        const candidates = [];
        if (configured) {
            candidates.push(configured);
            candidates.push(path.join(configured, 'bin', exe));
        }
        if (process.env.JAVA_HOME) {
            candidates.push(path.join(process.env.JAVA_HOME, 'bin', exe));
        }
        candidates.push(
            'C:\\Program Files\\Java\\jdk-21\\bin\\java.exe',
            'C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe',
            'C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe',
            '/usr/bin/java',
            '/usr/lib/jvm/java-21/bin/java',
            '/opt/homebrew/opt/openjdk@21/bin/java'
        );

        let wrongVersion = null;
        for (const candidate of candidates) {
            if (!candidate) continue;
            let isFile = false;
            try {
                isFile = fs.existsSync(candidate) && !fs.statSync(candidate).isDirectory();
            } catch (_) { /* ignore */ }
            if (!isFile) continue;
            const check = MinecraftLauncher.checkJavaVersion(candidate);
            if (check.ok) return candidate;
            if (check.version !== null && wrongVersion === null) wrongVersion = check.version;
        }

        // PATH — проверяем так же строго, чтобы не запустить Java 8/17.
        const pathCheck = MinecraftLauncher.checkJavaVersion(exe);
        if (pathCheck.ok) return exe;
        if (pathCheck.version !== null && wrongVersion === null) wrongVersion = pathCheck.version;

        if (wrongVersion !== null) {
            throw new Error(
                `Найдена Java ${wrongVersion}, а для Minecraft 1.21.4 нужна Java 21+. ` +
                'Установите JDK 21 (Temurin/Microsoft) или укажите путь к java в настройках лаунчера.'
            );
        }
        throw new Error(
            'Java 21 не найдена. Установите JDK 21 и перезапустите лаунчер, ' +
            'или укажите путь к java вручную в настройках.'
        );
    }

    findJarInDirectory(directory) {
        if (!directory || !fs.existsSync(directory)) return null;
        let entries;
        try {
            entries = fs.readdirSync(directory, { withFileTypes: true });
        } catch (_) {
            return null;
        }
        const jars = entries
            .filter(entry => entry.isFile() && CLIENT_JAR_PATTERN.test(entry.name))
            .map(entry => {
                const fullPath = path.join(directory, entry.name);
                return { path: fullPath, mtime: fs.statSync(fullPath).mtimeMs };
            })
            .sort((a, b) => b.mtime - a.mtime);
        return jars[0]?.path || null;
    }

    /**
     * Ищет собранный JAR Laura Client. В релизе он попадает в resources/client,
     * а в режиме разработки берётся из build/libs или windows/.
     */
    findClientJar() {
        const configured = this.config.game.clientJarPath;
        if (configured) {
            const resolved = path.resolve(configured);
            if (fs.existsSync(resolved) && fs.statSync(resolved).isFile() && resolved.toLowerCase().endsWith('.jar')) {
                return resolved;
            }
            if (fs.existsSync(resolved) && fs.statSync(resolved).isDirectory()) {
                const jar = this.findJarInDirectory(resolved);
                if (jar) return jar;
            }
            throw new Error(`JAR клиента не найден: ${configured}`);
        }

        const roots = [
            path.join(process.resourcesPath || '', 'client'),
            path.join(process.resourcesPath || '', 'app.asar.unpacked', 'client'),
            path.join(__dirname, '..', '..', '..', 'build', 'libs'),
            path.join(__dirname, '..', '..', '..', 'windows'),
            path.join(process.cwd(), 'build', 'libs'),
            path.join(process.cwd(), 'windows'),
            path.dirname(process.execPath)
        ];
        for (const root of roots) {
            const jar = this.findJarInDirectory(root);
            if (jar) return jar;
        }

        throw new Error(
            'JAR Laura Client не найден. Соберите проект командой «gradlew build» ' +
            'или укажите путь к JAR в настройках лаунчера.'
        );
    }

    installClientJar(instancePath, clientJar) {
        const modsPath = path.join(instancePath, 'mods');
        fs.mkdirSync(modsPath, { recursive: true });

        // Убираем только старые сборки Laura Client, не трогая сторонние моды.
        for (const entry of fs.readdirSync(modsPath, { withFileTypes: true })) {
            if (entry.isFile() && CLIENT_JAR_PATTERN.test(entry.name)) {
                try { fs.rmSync(path.join(modsPath, entry.name), { force: true }); } catch (_) { /* best effort */ }
            }
        }

        const destination = path.join(modsPath, path.basename(clientJar));
        fs.copyFileSync(clientJar, destination);
        return destination;
    }

    /**
     * Гарантирует, что в папке mods лежит Fabric API.
     *
     * Это критично: Laura Client в fabric.mod.json объявляет зависимость
     * "fabric-api": "*", и без него Fabric-лоадер роняет игру сразу после
     * старта (окно Java открывается и тут же закрывается, «майны нет»).
     * PowerShell-лаунчер (windows/LauraLauncher.ps1) ставит его с Modrinth —
     * Electron-лаунчер должен делать то же самое.
     *
     * Идемпотентно: если fabric-api-*.jar уже в mods (поставлен вручную или
     * прошлым запуском), ничего не скачиваем.
     */
    async ensureFabricApi(instancePath, version) {
        const modsPath = path.join(instancePath, 'mods');
        fs.mkdirSync(modsPath, { recursive: true });

        let existing = [];
        try {
            existing = fs.readdirSync(modsPath, { withFileTypes: true })
                .filter(entry => entry.isFile())
                .map(entry => entry.name);
        } catch (_) { /* папка пустая или недоступна — скачаем ниже */ }
        if (existing.some(name => FABRIC_API_JAR_PATTERN.test(name))) {
            this.report('Fabric API уже на месте');
            return null;
        }

        this.report('Скачиваем Fabric API (нужен клиенту)...');
        let file = null;
        try {
            const query = `?game_versions=${encodeURIComponent(JSON.stringify([version]))}` +
                `&loaders=${encodeURIComponent(JSON.stringify(['fabric']))}`;
            const versions = await MinecraftLauncher.getJson(MODRINTH_API_URL + query);
            if (!Array.isArray(versions) || versions.length === 0) {
                throw new Error('пустой ответ Modrinth');
            }
            // В приоритете версия, под которую собран клиент, иначе — первый
            // совместимый с этой версией Minecraft релиз.
            const chosen = versions.find(item =>
                String(item.version_number || '').startsWith(PREFERRED_FABRIC_API_VERSION)) || versions[0];
            file = (chosen.files || []).find(item => FABRIC_API_JAR_PATTERN.test(item.filename));
            if (!file) throw new Error('в ответе Modrinth нет файла fabric-api-*.jar');
        } catch (error) {
            throw new Error(
                `Не удалось скачать Fabric API: ${error.message}. ` +
                `Скачайте fabric-api-${PREFERRED_FABRIC_API_VERSION}+${version}.jar с ` +
                `https://modrinth.com/mod/fabric-api и положите в: ${modsPath}`
            );
        }

        const destination = path.join(modsPath, path.basename(file.filename));
        await MinecraftLauncher.downloadFile(file.url, destination);
        this.report(`Fabric API установлен: ${path.basename(file.filename)}`);
        return destination;
    }

    static isLibraryAllowed(library) {
        const rules = Array.isArray(library.rules) ? library.rules : [];
        if (!rules.length) return true;
        const osName = PLATFORM_OS[process.platform] || process.platform;
        const arch = process.arch === 'arm64' ? 'arm64' : '64';
        let allowed = false;

        for (const rule of rules) {
            const ruleOs = rule.os || {};
            const osMatches = !ruleOs.name || ruleOs.name === osName;
            const archMatches = !ruleOs.arch || ruleOs.arch === arch;
            const featureMatches = Object.entries(rule.features || {}).every(([name, value]) => {
                const features = {
                    is_demo_user: false,
                    has_custom_resolution: false
                };
                return features[name] === Boolean(value);
            });
            if (osMatches && archMatches && featureMatches) {
                allowed = rule.action !== 'disallow';
            }
        }
        return allowed;
    }

    // Из выбранной в диалоге папки строит путь к исполняемому файлу java.
    // Принимает и корень JDK, и саму папку bin. Возвращает самый вероятный
    // путь, даже если файл пока не найден (пользователь увидит его в поле).
    static resolveJavaExecutable(directory) {
        if (!directory) return null;
        const exe = process.platform === 'win32' ? 'java.exe' : 'java';
        const base = path.normalize(String(directory).trim());
        const direct = path.join(base, exe);                 // выбрали саму bin
        const inBin = path.join(base, 'bin', exe);           // выбрали корень JDK
        const inJreBin = path.join(base, 'jre', 'bin', exe); // JDK с вложенной jre
        for (const candidate of [direct, inBin, inJreBin]) {
            try {
                if (fs.existsSync(candidate) && fs.statSync(candidate).isFile()) return candidate;
            } catch (_) { /* ignore */ }
        }
        return inBin;
    }

    static coordinateToPath(coordinate) {
        const parts = String(coordinate || '').split(':');
        if (parts.length < 3) return null;
        const [group, artifact, version, classifier] = parts;
        const file = `${artifact}-${version}${classifier ? `-${classifier}` : ''}.jar`;
        return `${group.replace(/\./g, '/')}/${artifact}/${version}/${file}`;
    }

    static safeRelativePath(relativePath) {
        const normalized = path.normalize(relativePath);
        if (path.isAbsolute(normalized) || normalized.startsWith(`..${path.sep}`) || normalized === '..') {
            throw new Error(`Небезопасный путь библиотеки: ${relativePath}`);
        }
        return normalized;
    }

    async downloadLibraries(libraries, librariesPath, nativesPath, javaPath) {
        const artifacts = [];
        const seen = new Set();
        const nativeJars = [];

        for (const library of libraries || []) {
            if (!library || !MinecraftLauncher.isLibraryAllowed(library)) continue;
            const key = library.name || JSON.stringify(library);
            if (seen.has(key)) continue;
            seen.add(key);

            const downloads = library.downloads || {};
            const artifact = downloads.artifact || {};
            const artifactPath = artifact.path || MinecraftLauncher.coordinateToPath(library.name);
            const artifactUrl = artifact.url || (artifactPath
                ? new URL(artifactPath, library.url || 'https://libraries.minecraft.net/').toString()
                : null);
            if (artifactPath && artifactUrl) {
                const safePath = MinecraftLauncher.safeRelativePath(artifactPath);
                const destination = path.join(librariesPath, safePath);
                await MinecraftLauncher.downloadFile(artifactUrl, destination);
                artifacts.push(destination);
            }

            const nativeKey = PLATFORM_OS[process.platform];
            const nativeTemplate = library.natives && nativeKey ? library.natives[nativeKey] : null;
            if (!nativeTemplate) continue;
            const classifier = String(nativeTemplate).replace('${arch}', process.arch === 'arm64' ? '64' : '64');
            const native = (downloads.classifiers && downloads.classifiers[classifier]) || {};
            const nativePath = native.path || MinecraftLauncher.coordinateToPath(
                library.name ? `${library.name}:${classifier}` : ''
            );
            const nativeUrl = native.url || (nativePath
                ? new URL(nativePath, library.url || 'https://libraries.minecraft.net/').toString()
                : null);
            if (!nativePath || !nativeUrl) continue;
            const safeNativePath = MinecraftLauncher.safeRelativePath(nativePath);
            const nativeJar = path.join(librariesPath, safeNativePath);
            await MinecraftLauncher.downloadFile(nativeUrl, nativeJar);
            nativeJars.push(nativeJar);
        }

        fs.mkdirSync(nativesPath, { recursive: true });
        for (const nativeJar of nativeJars) {
            // Сначала пробуем штатный `jar` из JDK, но если стоит только JRE
            // (без jar.exe) — распаковываем нативки встроенным unzip на zlib,
            // чтобы запуск не падал с «Не удалось распаковать».
            let extracted = false;
            // Соседний `jar` имеет смысл искать только если java задан полным путём.
            if (javaPath && javaPath.includes(path.sep)) {
                try {
                    const jarCommand = process.platform === 'win32'
                        ? path.join(path.dirname(javaPath), 'jar.exe')
                        : path.join(path.dirname(javaPath), 'jar');
                    if (fs.existsSync(jarCommand)) {
                        const result = spawnSync(jarCommand, ['xf', nativeJar], {
                            cwd: nativesPath,
                            windowsHide: true,
                            stdio: 'ignore'
                        });
                        extracted = !result.error && result.status === 0;
                    }
                } catch (_) { /* fallback ниже */ }
            }
            if (!extracted) {
                try {
                    const fallback = spawnSync('jar', ['xf', nativeJar], {
                        cwd: nativesPath,
                        windowsHide: true,
                        stdio: 'ignore'
                    });
                    extracted = !fallback.error && fallback.status === 0;
                } catch (_) { /* fallback ниже */ }
            }
            if (!extracted) {
                MinecraftLauncher.extractZip(nativeJar, nativesPath);
            }
        }
        return artifacts;
    }

    // Минимальный unzip без внешних программ: stored + deflate.
    // Как и ванильный лаунчер, пропускаем META-INF в нативных библиотеках.
    static extractZip(zipPath, destination) {
        let buffer;
        try {
            buffer = fs.readFileSync(zipPath);
        } catch (error) {
            throw new Error(`Не удалось прочитать архив ${path.basename(zipPath)}: ${error.message}`);
        }
        if (buffer.length < 22) throw new Error(`Повреждённый архив: ${path.basename(zipPath)}`);

        // Ищем EOCD с конца файла (максимум 64К комментарий).
        let eocd = -1;
        const scanStart = Math.max(0, buffer.length - (0xFFFF + 22));
        for (let i = buffer.length - 22; i >= scanStart; i -= 1) {
            if (buffer.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
        }
        if (eocd < 0) throw new Error(`Не удалось распаковать ${path.basename(zipPath)}: нет EOCD.`);

        const entries = buffer.readUInt16LE(eocd + 10);
        let offset = buffer.readUInt32LE(eocd + 16);
        fs.mkdirSync(destination, { recursive: true });

        for (let n = 0; n < entries; n += 1) {
            if (offset + 46 > buffer.length || buffer.readUInt32LE(offset) !== 0x02014b50) break;
            const method = buffer.readUInt16LE(offset + 10);
            const compressedSize = buffer.readUInt32LE(offset + 20);
            const nameLength = buffer.readUInt16LE(offset + 28);
            const extraLength = buffer.readUInt16LE(offset + 30);
            const commentLength = buffer.readUInt16LE(offset + 32);
            const localOffset = buffer.readUInt32LE(offset + 42);
            const name = buffer.toString('utf8', offset + 46, offset + 46 + nameLength);
            offset += 46 + nameLength + extraLength + commentLength;

            if (!name || name.endsWith('/') || name.startsWith('META-INF/')) continue;
            const normalized = path.normalize(name);
            if (path.isAbsolute(normalized) || normalized.startsWith(`..${path.sep}`) || normalized === '..') continue;
            if (localOffset + 30 > buffer.length) continue;
            const localNameLen = buffer.readUInt16LE(localOffset + 26);
            const localExtraLen = buffer.readUInt16LE(localOffset + 28);
            const dataStart = localOffset + 30 + localNameLen + localExtraLen;
            const dataEnd = dataStart + compressedSize;
            if (dataEnd > buffer.length) continue;

            let data = buffer.subarray(dataStart, dataEnd);
            if (method === 8) {
                try {
                    data = zlib.inflateRawSync(data);
                } catch (error) {
                    throw new Error(`Не удалось распаковать ${name}: ${error.message}`);
                }
            } else if (method !== 0) {
                continue; // неизвестный метод сжатия — пропускаем запись
            }
            const outPath = path.join(destination, normalized);
            fs.mkdirSync(path.dirname(outPath), { recursive: true });
            fs.writeFileSync(outPath, data);
        }
    }

    async downloadAssets(assetIndex, assetsPath) {
        if (!assetIndex || !assetIndex.id || !assetIndex.url) return;
        const indexPath = path.join(assetsPath, 'indexes', `${assetIndex.id}.json`);
        if (!fs.existsSync(indexPath)) {
            await MinecraftLauncher.downloadFile(assetIndex.url, indexPath);
        }
        let index;
        try {
            index = JSON.parse(fs.readFileSync(indexPath, 'utf8'));
        } catch (error) {
            throw new Error(`Не удалось открыть индекс ресурсов Minecraft: ${error.message}`);
        }

        const objects = Object.values(index.objects || {});
        const queue = [...objects];
        const worker = async () => {
            while (queue.length) {
                const object = queue.shift();
                if (!object?.hash) continue;
                const prefix = object.hash.slice(0, 2);
                const destination = path.join(assetsPath, 'objects', prefix, object.hash);
                const url = `https://resources.download.minecraft.net/${prefix}/${object.hash}`;
                await MinecraftLauncher.downloadFile(url, destination);
            }
        };
        await Promise.all(Array.from({ length: Math.min(8, Math.max(1, objects.length)) }, worker));
        return index;
    }

    async resolveProfiles(version) {
        const manifest = await MinecraftLauncher.getJson(MINECRAFT_MANIFEST_URL);
        const entry = (manifest.versions || []).find(item => item.id === version);
        if (!entry) throw new Error(`Minecraft ${version} не найден в официальном манифесте.`);

        const baseProfile = await MinecraftLauncher.getJson(entry.url);
        const loaderVersion = this.config.game.fabricLoaderVersion || DEFAULT_FABRIC_LOADER;
        let fabricProfile;
        let resolvedLoaderVersion = loaderVersion;
        try {
            fabricProfile = await MinecraftLauncher.getJson(
                `${FABRIC_META_URL}/${encodeURIComponent(version)}/${encodeURIComponent(loaderVersion)}/profile/json`
            );
        } catch (error) {
            // Если сохранённая версия Fabric устарела, выбираем первый стабильный loader.
            const loaders = await MinecraftLauncher.getJson(`${FABRIC_META_URL}/${encodeURIComponent(version)}`);
            const stable = (loaders || []).find(item => item.stable !== false) || loaders?.[0];
            if (!stable?.loader?.version) {
                throw new Error(`Не удалось получить профиль Fabric: ${error.message}`);
            }
            resolvedLoaderVersion = stable.loader.version;
            fabricProfile = await MinecraftLauncher.getJson(
                `${FABRIC_META_URL}/${encodeURIComponent(version)}/${encodeURIComponent(resolvedLoaderVersion)}/profile/json`
            );
        }

        const libraries = [];
        const libraryKeys = new Set();
        for (const library of [...(baseProfile.libraries || []), ...(fabricProfile.libraries || [])]) {
            const key = library.name || JSON.stringify(library);
            if (!libraryKeys.has(key)) {
                libraryKeys.add(key);
                libraries.push(library);
            }
        }

        const fabricMainClass = typeof fabricProfile.mainClass === 'string'
            ? fabricProfile.mainClass
            : fabricProfile.mainClass?.client;
        const baseMainClass = typeof baseProfile.mainClass === 'string'
            ? baseProfile.mainClass
            : baseProfile.mainClass?.client;

        return {
            base: baseProfile,
            fabric: fabricProfile,
            profile: {
                ...baseProfile,
                ...fabricProfile,
                id: fabricProfile.id || `fabric-loader-${resolvedLoaderVersion}-${version}`,
                mainClass: fabricMainClass || baseMainClass || 'net.fabricmc.loader.impl.launch.knot.KnotClient',
                assetIndex: fabricProfile.assetIndex || baseProfile.assetIndex,
                libraries,
                // Складываем аргументы как официальный лаунчер: base + fabric.
                // Прежнее поведение (fabric «заменяет» base, когда у него
                // непустой массив) молча теряло базовые jvm-аргументы
                // (-Djava.library.path, -Dorg.lwjgl.system.SharedLibraryExtractPath
                // и т.п.), если профиль лоадера их содержал.
                // Свой -cp/-Xms/-Xmx из профилей buildLaunchArgs и так отфильтровывает.
                arguments: {
                    jvm: [...(baseProfile.arguments?.jvm || []), ...(fabricProfile.arguments?.jvm || [])],
                    game: [...(baseProfile.arguments?.game || []), ...(fabricProfile.arguments?.game || [])]
                },
                minecraftArguments: fabricProfile.minecraftArguments || baseProfile.minecraftArguments
            },
            loaderVersion: resolvedLoaderVersion
        };
    }

    static replacePlaceholders(value, replacements) {
        return String(value).replace(/\$\{([^}]+)\}/g, (match, key) => {
            return Object.prototype.hasOwnProperty.call(replacements, key)
                ? String(replacements[key])
                : match;
        });
    }

    static resolveArguments(rawArguments, replacements) {
        const result = [];
        for (const argument of rawArguments || []) {
            if (typeof argument === 'string') {
                result.push(MinecraftLauncher.replacePlaceholders(argument, replacements));
                continue;
            }
            const rules = Array.isArray(argument.rules) ? argument.rules : [];
            let allowed = true;
            if (rules.length) {
                allowed = false;
                for (const rule of rules) {
                    const ruleOs = rule.os || {};
                    const currentOs = PLATFORM_OS[process.platform] || process.platform;
                    const osMatches = !ruleOs.name || ruleOs.name === currentOs;
                    const archMatches = !ruleOs.arch || ruleOs.arch === (process.arch === 'arm64' ? 'arm64' : '64');
                    const featureMatches = Object.entries(rule.features || {}).every(([name, value]) => {
                        const features = { is_demo_user: false, has_custom_resolution: true };
                        return features[name] === Boolean(value);
                    });
                    if (osMatches && archMatches && featureMatches) {
                        allowed = rule.action !== 'disallow';
                    }
                }
            }
            if (!allowed) continue;
            const values = Array.isArray(argument.value) ? argument.value : [argument.value];
            for (const value of values) {
                result.push(MinecraftLauncher.replacePlaceholders(value, replacements));
            }
        }
        return result;
    }

    buildLaunchArgs(profile, prepared, gameConfig) {
        const username = this.config.profile.nickname || 'Player';
        const uuid = this.config.profile.uuid || crypto.randomUUID();
        const accessToken = '0';
        const versionName = profile.id;
        const separator = path.delimiter;
        const classpath = prepared.classpath.join(separator);
        const replacements = {
            auth_player_name: username,
            version_name: versionName,
            game_directory: prepared.instancePath,
            assets_root: prepared.assetsPath,
            assets_index_name: profile.assetIndex?.id || '',
            auth_uuid: uuid,
            auth_access_token: accessToken,
            auth_session: `token:${accessToken}:${uuid}`,
            user_type: this.config.profile.authMode === 'offline' ? 'legacy' : 'mojang',
            version_type: profile.type || 'release',
            user_properties: '{}',
            clientid: '0',
            auth_xuid: '',
            auth_device_id: uuid,
            natives_directory: prepared.nativesPath,
            launcher_name: 'LauraLauncher',
            launcher_version: '1.0.0',
            classpath,
            classpath_separator: separator,
            resolution_width: gameConfig.width,
            resolution_height: gameConfig.height
        };

        const rawJvm = profile.arguments?.jvm || [];
        const rawGame = profile.arguments?.game || [];
        let jvmArgs = MinecraftLauncher.resolveArguments(rawJvm, replacements);
        let gameArgs = MinecraftLauncher.resolveArguments(rawGame, replacements);

        // Профили Mojang/Fabric иногда содержат свой classpath и память —
        // лаунчер задаёт их сам, чтобы JAR клиента всегда был в classpath.
        const filteredJvm = [];
        for (let i = 0; i < jvmArgs.length; i += 1) {
            const value = jvmArgs[i];
            if (value === '-cp' || value === '-classpath') {
                i += 1;
                continue;
            }
            if (/^-Xms\d+[MG]$/i.test(value) || /^-Xmx\d+[MG]$/i.test(value)) continue;
            filteredJvm.push(value);
        }
        jvmArgs = filteredJvm;

        const hasGameArg = name => gameArgs.includes(name);
        const appendGameArg = (name, value) => {
            if (!hasGameArg(name)) gameArgs.push(name, String(value));
        };
        appendGameArg('--username', username);
        appendGameArg('--version', versionName);
        appendGameArg('--gameDir', prepared.instancePath);
        appendGameArg('--assetsDir', prepared.assetsPath);
        if (profile.assetIndex?.id) appendGameArg('--assetIndex', profile.assetIndex.id);
        appendGameArg('--uuid', uuid);
        appendGameArg('--accessToken', accessToken);
        appendGameArg('--userProperties', '{}');
        appendGameArg('--userType', this.config.profile.authMode === 'offline' ? 'legacy' : 'mojang');
        if (gameConfig.fullscreen && !hasGameArg('--fullscreen')) gameArgs.push('--fullscreen');
        if (!gameConfig.fullscreen) {
            appendGameArg('--width', gameConfig.width || 854);
            appendGameArg('--height', gameConfig.height || 480);
        }

        return [
            `-Xms${gameConfig.ramMin || 2048}M`,
            `-Xmx${gameConfig.ramMax || 4096}M`,
            '-Dfile.encoding=UTF-8',
            `-Djava.library.path=${prepared.nativesPath}`,
            ...jvmArgs,
            '-cp',
            classpath,
            profile.mainClass,
            ...gameArgs
        ];
    }

    async prepare(instancePath, clientJar) {
        const gameConfig = this.config.game;
        const version = gameConfig.version || DEFAULT_VERSION;
        const profiles = await this.resolveProfiles(version);
        const profile = profiles.profile;
        const versionsPath = path.join(instancePath, 'versions');
        const librariesPath = path.join(instancePath, 'libraries');
        const assetsPath = path.join(instancePath, 'assets');
        const nativesPath = path.join(instancePath, `natives-${PLATFORM_OS[process.platform] || process.platform}`);
        const versionPath = path.join(versionsPath, version);
        const clientGameJar = path.join(versionPath, `${version}.jar`);

        fs.mkdirSync(versionPath, { recursive: true });
        fs.mkdirSync(librariesPath, { recursive: true });
        fs.mkdirSync(assetsPath, { recursive: true });

        // Fabric API до тяжёлых скачиваний: без него игра всё равно упадёт,
        // поэтому при недоступности Modrinth лучше упасть сразу с понятной
        // ошибкой, чем после десятка минут загрузки.
        await this.ensureFabricApi(instancePath, version);

        const gameDownload = profiles.base.downloads?.client || profile.downloads?.client;
        if (!gameDownload?.url) throw new Error(`В профиле Minecraft ${version} отсутствует client JAR.`);
        await MinecraftLauncher.downloadFile(gameDownload.url, clientGameJar);
        await this.downloadAssets(profile.assetIndex, assetsPath);
        const libraryFiles = await this.downloadLibraries(
            profile.libraries,
            librariesPath,
            nativesPath,
            this.findJava()
        );

        return {
            instancePath,
            assetsPath,
            nativesPath,
            clientJar,
            profile,
            loaderVersion: profiles.loaderVersion,
            classpath: [...libraryFiles, clientGameJar]
        };
    }

    async launch(options = {}) {
        const gameConfig = this.config.game;
        if (options.loader && options.loader !== 'fabric') {
            throw new Error('Этот лаунчер поддерживает только Fabric.');
        }
        if (this.config.profile.authMode === 'microsoft') {
            throw new Error('Microsoft-авторизация пока не подключена. Выберите Offline в настройках.');
        }

        // Java проверяем ДО долгих скачиваний, чтобы не качать гигабайты
        // ради запуска, который всё равно упадёт без JDK 21.
        this.report('Проверка Java 21...');
        const java = this.findJava();
        const instancePath = MinecraftLauncher.resolveInstancePath(this.config);
        fs.mkdirSync(instancePath, { recursive: true });

        this.report('Поиск JAR Laura Client...');
        const clientJar = this.findClientJar();
        this.installClientJar(instancePath, clientJar);
        this.report('Загрузка файлов Minecraft (первый запуск может занять несколько минут)...');
        const prepared = await this.prepare(instancePath, clientJar);
        const args = this.buildLaunchArgs(prepared.profile, prepared, gameConfig);
        const logPath = path.join(instancePath, 'laura-launcher.log');
        const log = fs.createWriteStream(logPath, { flags: 'a' });
        // spawn() принимает только уже открытые потоки: если передать
        // WriteStream до события open — будет ERR_INVALID_ARG_VALUE и запуск
        // молча упадёт на последнем шаге. Ждём открытия лог-файла.
        await new Promise((resolve, reject) => {
            if (log.fd !== null && log.fd !== undefined && log.fd >= 0) {
                resolve();
                return;
            }
            log.once('open', resolve);
            log.once('error', () => reject(new Error(`Не удалось открыть лог-файл ${logPath}`)));
        });
        this.report('Запуск игры...');
        const child = spawn(java, args, {
            cwd: instancePath,
            windowsHide: false,
            stdio: ['ignore', log, log]
        });

        try {
            const stamp = new Date().toISOString();
            if (this.configStore) {
                this.configStore.set({ lastPlayed: stamp });
                this.config = this.configStore.get();
            } else if (this.config && typeof this.config === 'object') {
                this.config.lastPlayed = stamp;
            }
        } catch (_) { /* best effort: отметка времени не должна ронять запуск */ }

        return new Promise((resolve, reject) => {
            // Лог закрываем один раз: процесс мог завершиться и с ошибкой,
            // и нормально — 'close' срабатывает в обоих случаях.
            const finishLog = () => {
                if (!log.destroyed) {
                    try { log.end(); } catch (_) { /* best effort */ }
                }
            };
            child.once('error', error => {
                finishLog();
                reject(new Error(`Не удалось запустить Java: ${error.message}`));
            });
            child.once('spawn', () => {
                child.once('close', code => {
                    finishLog();
                    // Окно Java закрылось не через меню «Выход» — вместо
                    // молчания показываем причину: код и путь к логу, где
                    // есть полный стек (наиболее частые причины — недостающий
                    // Fabric API или несовместимый мод).
                    if (code !== null && code !== 0) {
                        this.report(
                            `Игра завершилась с ошибкой (код ${code}). ` +
                            `Полный лог: ${logPath}`
                        );
                    }
                });
                resolve({
                    mode: 'process',
                    pid: child.pid,
                    java,
                    loader: 'fabric',
                    clientJar,
                    instancePath,
                    message: 'Laura Client запущен на Fabric.'
                });
            });
        });
    }
}

module.exports = MinecraftLauncher;
