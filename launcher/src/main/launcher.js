// Логика запуска Minecraft
const { spawn } = require('child_process');
const path = require('path');
const fs = require('fs');
const https = require('https');

class MinecraftLauncher {
    constructor(config) {
        this.config = config;
    }

    // Получить список версий с Mojang
    static async fetchVersions(type = 'release') {
        return new Promise((resolve, reject) => {
            https.get('https://launchermeta.mojang.com/mc/game/version_manifest_v2.json', (res) => {
                let data = '';
                res.on('data', (chunk) => data += chunk);
                res.on('end', () => {
                    try {
                        const manifest = JSON.parse(data);
                        const versions = manifest.versions
                            .filter(v => v.type === type)
                            .slice(0, 50)
                            .map(v => ({ id: v.id, type: v.type, url: v.url }));
                        resolve(versions);
                    } catch (err) {
                        reject(err);
                    }
                });
            }).on('error', reject);
        });
    }

    // Найти Java
    findJava() {
        if (this.config.game.javaPath && fs.existsSync(this.config.game.javaPath)) {
            return this.config.game.javaPath;
        }
        // Ищем в стандартных путях
        const candidates = [
            'C:\\Program Files\\Java\\jdk-21\\bin\\java.exe',
            'C:\\Program Files\\Eclipse Adoptium\\jdk-21\\bin\\java.exe',
            'C:\\Program Files\\Microsoft\\jdk-21\\bin\\java.exe',
            '/usr/bin/java',
            '/usr/lib/jvm/java-21/bin/java'
        ];
        for (const p of candidates) {
            if (fs.existsSync(p)) return p;
        }
        return 'java'; // PATH
    }

    // Скачать файл
    static downloadFile(url, dest) {
        return new Promise((resolve, reject) => {
            const file = fs.createWriteStream(dest);
            https.get(url, (response) => {
                if (response.statusCode === 302 || response.statusCode === 301) {
                    return MinecraftLauncher.downloadFile(response.headers.location, dest)
                        .then(resolve).catch(reject);
                }
                response.pipe(file);
                file.on('finish', () => file.close(resolve));
            }).on('error', (err) => {
                fs.unlink(dest, () => reject(err));
            });
        });
    }

    async launch(options = {}) {
        const gameConfig = this.config.game;
        const instancePath = gameConfig.instancePath || path.join(process.env.LOCALAPPDATA || process.env.HOME, 'SakuraClient');

        if (!fs.existsSync(instancePath)) {
            fs.mkdirSync(instancePath, { recursive: true });
        }

        // Заглушка: в реальном лаунчере здесь скачивание клиента, библиотек, ассетов, аутентификация
        // и формирование classpath. Сейчас просто демонстрируем запуск.

        const java = this.findJava();
        const args = [
            `-Xms${gameConfig.ramMin}M`,
            `-Xmx${gameConfig.ramMax}M`,
            '-Dfile.encoding=UTF-8',
            '-cp', '<classpath>',
            'net.minecraft.client.main.Main',
            '--username', this.config.profile.nickname,
            '--version', gameConfig.version,
            '--gameDir', instancePath,
            '--assetsDir', path.join(instancePath, 'assets'),
            '--userProperties', '{}',
            '--userType', this.config.profile.authMode === 'offline' ? 'legacy' : 'mojang'
        ];

        if (gameConfig.fullscreen) args.push('--fullscreen');

        this.config.data.lastPlayed = new Date().toISOString();
        this.config.save();

        // В демо-режиме просто пишем что запустилось бы
        return {
            java,
            args,
            cwd: instancePath,
            mode: 'demo',
            message: 'В демо-режиме запуск не выполняется. Это заглушка GUI-лаунчера.'
        };
    }
}

module.exports = MinecraftLauncher;
