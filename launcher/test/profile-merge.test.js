// Тесты слияния профилей и сборки classpath (node --test, без зависимостей).
//
// Регресс на «duplicate ASM classes found on classpath»:
//   Exception in thread "main" java.lang.ExceptionInInitializerError
//   Caused by: java.lang.IllegalStateException: duplicate ASM classes found on classpath:
//     .../libraries/org/ow2/asm/asm/9.6/asm-9.6.jar!/org/objectweb/asm/ClassReader.class,
//     .../libraries/org/ow2/asm/asm/9.9/asm-9.9.jar!/org/objectweb/asm/ClassReader.class
//       at net.fabricmc.loader.impl.util.LoaderUtil.verifyClasspath(LoaderUtil.java:83)
//       at net.fabricmc.loader.impl.launch.knot.Knot.<clinit>(Knot.java:330)
//
// Профиль Mojang для 1.21.4 declares org.ow2.asm:*:9.6, профиль Fabric Loader
// 0.18.4 — org.ow2.asm:*:9.9. Дедуп «по полному координате» оставлял обе, и
// лоадер убивал запуск ещё до окна игры.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const MinecraftLauncher = require('../src/main/launcher');

// --- фрагмент базового профиля Minecraft 1.21.4 (Mojang) ---
const mojangLibrary = (name, extra = {}) => ({
    name,
    downloads: {
        artifact: {
            path: `${name.split(':')[0].replace(/\./g, '/')}/${name.split(':')[1]}/${name.split(':')[2]}/`
                + `${name.split(':')[1]}-${name.split(':')[2]}${name.split(':')[3] ? `-${name.split(':')[3]}` : ''}.jar`,
            url: `https://libraries.minecraft.net/${name}`
        }
    },
    ...extra
});

const BASE_LIBRARIES = [
    mojangLibrary('org.ow2.asm:asm:9.6'),
    mojangLibrary('org.ow2.asm:asm-analysis:9.6'),
    mojangLibrary('org.ow2.asm:asm-commons:9.6'),
    mojangLibrary('org.ow2.asm:asm-tree:9.6'),
    mojangLibrary('org.ow2.asm:asm-util:9.6'),
    mojangLibrary('com.google.code.gson:gson:2.11.0'),
    mojangLibrary('org.apache.logging.log4j:log4j-api:2.19.0'),
    mojangLibrary('org.lwjgl:lwjgl:3.3.4', {
        rules: [{ action: 'allow' }, { action: 'disallow', os: { name: 'osx' } }]
    }),
    mojangLibrary('org.lwjgl:lwjgl:3.3.4:natives-windows', {
        rules: [{ action: 'allow', os: { name: 'windows' } }]
    }),
    mojangLibrary('ca.weblite:java-objc-bridge:1.1:natives-macos-arm64', {
        rules: [{ action: 'allow', os: { name: 'osx', arch: 'arm64' } }]
    })
];

// --- реальный профиль fabric-loader 0.18.4 для 1.21.4 (meta.fabricmc.net) ---
const LOADER_LIBRARIES = [
    { name: 'org.ow2.asm:asm:9.9', url: 'https://maven.fabricmc.net/' },
    { name: 'org.ow2.asm:asm-analysis:9.9', url: 'https://maven.fabricmc.net/' },
    { name: 'org.ow2.asm:asm-commons:9.9', url: 'https://maven.fabricmc.net/' },
    { name: 'org.ow2.asm:asm-tree:9.9', url: 'https://maven.fabricmc.net/' },
    { name: 'org.ow2.asm:asm-util:9.9', url: 'https://maven.fabricmc.net/' },
    { name: 'net.fabricmc:sponge-mixin:0.17.0+mixin.0.8.7', url: 'https://maven.fabricmc.net/' },
    { name: 'net.fabricmc:intermediary:1.21.4', url: 'https://maven.fabricmc.net/' },
    { name: 'net.fabricmc:fabric-loader:0.18.4', url: 'https://maven.fabricmc.net/' }
];

test('profile merge keeps exactly one version of every artifact', () => {
    const merged = MinecraftLauncher.mergeLibraries(BASE_LIBRARIES, LOADER_LIBRARIES);
    const asm = merged.filter(library => library.name.startsWith('org.ow2.asm:'));

    assert.equal(asm.length, 5, 'ASM должен остаться ровно один набор — от лоадера');
    for (const library of asm) {
        assert.match(library.name, /:9\.9$/, `осталась старая версия ASM: ${library.name}`);
    }
    assert.ok(!merged.some(library => library.name.includes(':9.6')), 'версия 9.6 из профиля Mojang пропала');
});

test('profile merge preserves unrelated and platform-specific base libraries', () => {
    const merged = MinecraftLauncher.mergeLibraries(BASE_LIBRARIES, LOADER_LIBRARIES);
    const names = merged.map(library => library.name);

    for (const required of [
        'com.google.code.gson:gson:2.11.0',
        'org.apache.logging.log4j:log4j-api:2.19.0',
        'org.lwjgl:lwjgl:3.3.4',
        'org.lwjgl:lwjgl:3.3.4:natives-windows',
        'ca.weblite:java-objc-bridge:1.1:natives-macos-arm64'
    ]) {
        assert.ok(names.includes(required), `потерялась библиотека ${required}`);
    }
    // Библиотеки лоадера не дублируются и идут после базовых, как в официальном лаунчере.
    assert.equal(new Set(names).size, names.length, 'в merged-списке есть дубли');
    assert.ok(names.indexOf('net.fabricmc:fabric-loader:0.18.4') > names.indexOf('com.google.code.gson:gson:2.11.0'));
});

test('profile merge is idempotent and tolerates empty profiles', () => {
    const once = MinecraftLauncher.mergeLibraries(BASE_LIBRARIES, LOADER_LIBRARIES);
    const twice = MinecraftLauncher.mergeLibraries(once, LOADER_LIBRARIES);
    assert.deepEqual(twice.map(library => library.name), once.map(library => library.name));

    assert.deepEqual(MinecraftLauncher.mergeLibraries(undefined, LOADER_LIBRARIES).length, LOADER_LIBRARIES.length);
    assert.deepEqual(MinecraftLauncher.mergeLibraries(BASE_LIBRARIES, null), BASE_LIBRARIES);
});

test('classpath dedupe drops the older version even for an already merged list', () => {
    // Сценарий «профиль лежит в инстансе от старого лаунчера»: обе версии ASM
    // уже в списке, порядок может быть любым — в classpath уходит новая.
    const files = [
        { key: 'org.ow2.asm:asm', version: '9.9', file: '/libs/asm-9.9.jar' },
        { key: 'org.ow2.asm:asm', version: '9.6', file: '/libs/asm-9.6.jar' },
        { key: 'com.google.code.gson:gson', version: '2.11.0', file: '/libs/gson-2.11.0.jar' },
        { key: 'com.google.code.gson:gson', version: '2.11.0', file: '/libs/gson-2.11.0.jar' }
    ];
    assert.deepEqual(MinecraftLauncher.dedupeClasspath(files), ['/libs/asm-9.9.jar', '/libs/gson-2.11.0.jar']);

    const reversed = [...files].reverse();
    assert.deepEqual(MinecraftLauncher.dedupeClasspath(reversed), ['/libs/gson-2.11.0.jar', '/libs/asm-9.9.jar']);
});

test('libraryKey and libraryVersion understand coordinates and artifact paths', () => {
    assert.equal(MinecraftLauncher.libraryKey({ name: 'org.ow2.asm:asm:9.6' }), 'org.ow2.asm:asm');
    assert.equal(MinecraftLauncher.libraryKey({ name: 'org.lwjgl:lwjgl:3.3.4:natives-linux' }), 'org.lwjgl:lwjgl:natives-linux');
    assert.equal(MinecraftLauncher.libraryKey({
        downloads: { artifact: { path: 'org/ow2/asm/asm/9.6/asm-9.6.jar' } }
    }), 'org.ow2.asm:asm');
    assert.equal(MinecraftLauncher.libraryKey({ name: 'не-координата' }), null);

    assert.equal(MinecraftLauncher.libraryVersion({ name: 'net.fabricmc:fabric-loader:0.18.4' }), '0.18.4');
    assert.equal(MinecraftLauncher.libraryVersion({
        downloads: { artifact: { path: 'net/fabricmc/fabric-loader/0.18.4/fabric-loader-0.18.4.jar' } }
    }), '0.18.4');
});

test('artifact paths with a classifier are never collapsed into the main jar', () => {
    // Нативы Mojang описаны без координат, только путём — их выбрасывать нельзя.
    assert.equal(
        MinecraftLauncher.libraryKey({ downloads: { artifact: { path: 'org/lwjgl/lwjgl/3.3.4/lwjgl-3.3.4-natives-linux.jar' } } }),
        null
    );
    const classpath = MinecraftLauncher.dedupeClasspath([
        { key: null, version: '', file: '/libs/lwjgl-3.3.4.jar' },
        { key: null, version: '', file: '/libs/lwjgl-3.3.4-natives-linux.jar' }
    ]);
    assert.equal(classpath.length, 2, 'нативы не должны исчезать из classpath');
});

test('compareVersions handles maven-ish suffixes', () => {
    const { compareVersions } = MinecraftLauncher;
    assert.ok(compareVersions('9.6', '9.9') < 0);
    assert.ok(compareVersions('0.17.0+mixin.0.8.7', '0.17.1') < 0);
    assert.equal(compareVersions('1.21.4', '1.21.4'), 0);
    assert.ok(compareVersions('1.21.4', '1.21.4+build.8') < 0);
    assert.ok(compareVersions('3.28.0-GA', '3.28.1-GA') < 0);
});

test('describeCrash recognises the duplicate ASM crash in the log', () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'laura-launcher-'));
    try {
        const logPath = path.join(directory, 'laura-launcher.log');
        fs.writeFileSync(logPath, [
            'Exception in thread "main" java.lang.ExceptionInInitializerError',
            '\tat net.fabricmc.loader.impl.launch.knot.KnotClient.main(KnotClient.java:23)',
            'Caused by: java.lang.IllegalStateException: duplicate ASM classes found on classpath:',
            '\tjar:file:/C:/Users/USER/AppData/Local/Laura%20Client/instance/libraries/org/ow2/asm/asm/9.6/asm-9.6.jar'
                + '!/org/objectweb/asm/ClassReader.class, '
                + 'jar:file:/C:/Users/USER/AppData/Local/Laura%20Client/instance/libraries/org/ow2/asm/asm/9.9/asm-9.9.jar'
                + '!/org/objectweb/asm/ClassReader.class',
            '\tat net.fabricmc.loader.impl.util.LoaderUtil.verifyClasspath(LoaderUtil.java:83)',
            '\tat net.fabricmc.loader.impl.launch.knot.Knot.<clinit>(Knot.java:330)',
            '\t... 1 more'
        ].join('\n'), 'utf8');

        const hint = MinecraftLauncher.describeCrash(logPath);
        assert.ok(hint, 'подсказка не найдена');
        assert.match(hint, /ASM/);
        assert.match(hint, /libraries\/org\/ow2\/asm/);
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
});

test('describeCrash stays silent on unrelated or missing logs', () => {
    const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'laura-launcher-'));
    try {
        const logPath = path.join(directory, 'laura-launcher.log');
        fs.writeFileSync(logPath, '[12:00:00] [main/INFO]: Background resource reload\n', 'utf8');
        assert.equal(MinecraftLauncher.describeCrash(logPath), null);
        assert.equal(MinecraftLauncher.describeCrash(path.join(directory, 'nope.log')), null);
    } finally {
        fs.rmSync(directory, { recursive: true, force: true });
    }
});
