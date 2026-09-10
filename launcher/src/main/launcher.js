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

const FABRIC_META_URL = 'https://meta.fabricmc.net/v2/versions/loader';
const MINECRAFT_MANIFEST_URL = 'https://launchermeta.mojang.com/mc/game/version_manifest_v2.json';
const DEFAULT_FABRIC_LOADER = '0.18.4';
const DEFAULT_VERSION = '1.21.4';
const CLIENT_JAR_PATTERN = /^laura-client(?:-[^/]*)?\.jar$/i;

const PLATFORM_OS = {
    win32: 'windows',
    darwin: 'osx',
    linux: 'linux'
};

class MinecraftLauncher {
    constructor(config) {
        this.config = config;
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

    findJava() {
        const configured = this.config.game.javaPath;
        const candidates = [];
        if (configured) {
            candidates.push(configured);
            candidates.push(path.join(configured, 'bin', process.platform === 'win32' ? 'java.exe' : 'java'));
        }
        if (process.env.JAVA_HOME) {
            candidates.push(path.join(
                process.env.JAVA_HOME,
                'bin',
                process.platform === 'win32' ? 'java.exe' : 'java'
            ));
        }
        candidates.push(
            'C:\\Program Files\\Java\\jdk-21\\bin\\java.exe',
            'C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe',
            'C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe',
            '/usr/bin/java',
            '/usr/lib/jvm/java-21/bin/java',
            '/opt/homebrew/opt/openjdk@21/bin/java'
        );

        for (const candidate of candidates) {
            if (candidate && fs.existsSync(candidate) && !fs.statSync(candidate).isDirectory()) {
                return candidate;
            }
        }
        // PATH — spawn покажет понятную ошибку, если Java действительно отсутствует.
        return process.platform === 'win32' ? 'java.exe' : 'java';
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
            const jarCommand = process.platform === 'win32'
                ? path.join(path.dirname(javaPath), 'jar.exe')
                : path.join(path.dirname(javaPath), 'jar');
            const result = spawnSync(jarCommand, ['xf', nativeJar], {
                cwd: nativesPath,
                windowsHide: true,
                stdio: 'ignore'
            });
            if (result.error || result.status !== 0) {
                // В PATH может быть отдельный jar, например при использовании JAVA_HOME неявно.
                const fallback = spawnSync('jar', ['xf', nativeJar], {
                    cwd: nativesPath,
                    windowsHide: true,
                    stdio: 'ignore'
                });
                if (fallback.error || fallback.status !== 0) {
                    throw new Error('Не удалось распаковать нативные библиотеки Java. Установите JDK 21.');
                }
            }
        }
        return artifacts;
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
                arguments: {
                    jvm: fabricProfile.arguments?.jvm?.length
                        ? fabricProfile.arguments.jvm
                        : (baseProfile.arguments?.jvm || []),
                    game: fabricProfile.arguments?.game?.length
                        ? fabricProfile.arguments.game
                        : (baseProfile.arguments?.game || [])
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

        const baseDirectory = process.env.LOCALAPPDATA || process.env.APPDATA || os.homedir() || process.cwd();
        const instancePath = path.resolve(gameConfig.instancePath || path.join(baseDirectory, 'Laura Client', 'instance'));
        fs.mkdirSync(instancePath, { recursive: true });

        const clientJar = this.findClientJar();
        this.installClientJar(instancePath, clientJar);
        const prepared = await this.prepare(instancePath, clientJar);
        const java = this.findJava();
        const args = this.buildLaunchArgs(prepared.profile, prepared, gameConfig);
        const logPath = path.join(instancePath, 'laura-launcher.log');
        const log = fs.createWriteStream(logPath, { flags: 'a' });
        const child = spawn(java, args, {
            cwd: instancePath,
            windowsHide: false,
            stdio: ['ignore', log, log]
        });

        this.config.data.lastPlayed = new Date().toISOString();
        this.config.save();

        return new Promise((resolve, reject) => {
            child.once('error', error => {
                log.end();
                reject(new Error(`Не удалось запустить Java: ${error.message}`));
            });
            child.once('spawn', () => {
                child.once('close', () => log.end());
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
