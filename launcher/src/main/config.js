// Модуль работы с конфигом лаунчера
const fs = require('fs');
const os = require('os');
const path = require('path');

const FABRIC_LOADER = 'fabric';

const DEFAULT_CONFIG = {
    profile: {
        nickname: 'Player',
        uuid: '',
        authMode: 'offline' // offline | microsoft
    },
    game: {
        instancePath: '',
        version: '1.21.4',
        loader: FABRIC_LOADER,
        fabricLoaderVersion: '0.18.4',
        clientJarPath: '',
        ramMin: 2048,
        ramMax: 4096,
        javaPath: '',
        fullscreen: false,
        width: 854,
        height: 480,
        autoUpdate: true
    },
    ui: {
        accent: '#ff2d8a',
        theme: 'laura',
        animations: true
    },
    lastPlayed: null
};

class Config {
    constructor(customPath) {
        this.configPath = customPath || Config.resolveConfigPath();
        this.data = this.load();
    }

    // Путь к config.json. app.getPath('userData') до события ready может
    // бросить исключение и уронить весь лаунчер — поэтому здесь fallback
    // на домашнюю папку пользователя.
    static resolveConfigPath() {
        try {
            const { app } = require('electron');
            if (app && typeof app.getPath === 'function') {
                return path.join(app.getPath('userData'), 'config.json');
            }
        } catch (_) {
            // Electron ещё не готов или недоступен — используем fallback ниже.
        }
        const home = (os.homedir && os.homedir()) || process.cwd();
        return path.join(home, '.laura-launcher', 'config.json');
    }

    load() {
        try {
            if (fs.existsSync(this.configPath)) {
                const raw = fs.readFileSync(this.configPath, 'utf-8');
                return this.normalize(this.deepMerge(DEFAULT_CONFIG, JSON.parse(raw)));
            }
        } catch (err) {
            console.error('Config load error:', err);
        }
        const defaults = this.normalize(this.deepMerge({}, DEFAULT_CONFIG));
        this.save(defaults);
        return defaults;
    }

    save(data = this.data) {
        try {
            const dir = path.dirname(this.configPath);
            if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
            fs.writeFileSync(this.configPath, JSON.stringify(data, null, 2));
        } catch (err) {
            console.error('Config save error:', err);
        }
    }

    get() {
        return this.data;
    }

    set(patch) {
        this.data = this.normalize(this.deepMerge(this.data, patch));
        this.save();
        return this.data;
    }

    normalize(data) {
        const out = this.deepMerge({}, data || {});
        out.profile = out.profile || {};
        out.game = out.game || {};
        out.ui = out.ui || {};

        // Лаунчер поддерживает только Fabric: старые значения мигрируются
        // автоматически, а значение из renderer не может включить другой loader.
        out.game.loader = FABRIC_LOADER;
        out.game.version = String(out.game.version || DEFAULT_CONFIG.game.version);
        out.game.fabricLoaderVersion = String(
            out.game.fabricLoaderVersion || DEFAULT_CONFIG.game.fabricLoaderVersion
        );
        out.game.clientJarPath = typeof out.game.clientJarPath === 'string'
            ? out.game.clientJarPath
            : '';
        out.game.instancePath = typeof out.game.instancePath === 'string'
            ? out.game.instancePath
            : '';
        out.game.javaPath = typeof out.game.javaPath === 'string'
            ? out.game.javaPath
            : '';
        out.profile.nickname = String(out.profile.nickname || DEFAULT_CONFIG.profile.nickname)
            .replace(/[\r\n]/g, '')
            .slice(0, 16) || DEFAULT_CONFIG.profile.nickname;
        if (!['offline', 'microsoft'].includes(out.profile.authMode)) {
            out.profile.authMode = DEFAULT_CONFIG.profile.authMode;
        }
        return out;
    }

    deepMerge(target, source) {
        const out = { ...target };
        for (const key of Object.keys(source || {})) {
            if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
                out[key] = this.deepMerge(target[key] || {}, source[key]);
            } else {
                out[key] = source[key];
            }
        }
        return out;
    }
}

Config.FABRIC_LOADER = FABRIC_LOADER;
Config.DEFAULT_CONFIG = DEFAULT_CONFIG;

module.exports = Config;
