// Модуль работы с конфигом
const fs = require('fs');
const path = require('path');
const { app } = require('electron');

const DEFAULT_CONFIG = {
    profile: {
        nickname: 'Player',
        uuid: '',
        authMode: 'offline' // offline | microsoft
    },
    game: {
        instancePath: '',
        version: '1.21.4',
        loader: 'vanilla', // vanilla | forge | fabric
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
        theme: 'sakura',
        animations: true
    },
    lastPlayed: null
};

class Config {
    constructor() {
        this.configPath = path.join(app.getPath('userData'), 'config.json');
        this.data = this.load();
    }

    load() {
        try {
            if (fs.existsSync(this.configPath)) {
                const raw = fs.readFileSync(this.configPath, 'utf-8');
                return { ...DEFAULT_CONFIG, ...JSON.parse(raw) };
            }
        } catch (err) {
            console.error('Config load error:', err);
        }
        this.save(DEFAULT_CONFIG);
        return { ...DEFAULT_CONFIG };
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
        this.data = this.deepMerge(this.data, patch);
        this.save();
        return this.data;
    }

    deepMerge(target, source) {
        const out = { ...target };
        for (const key of Object.keys(source)) {
            if (source[key] && typeof source[key] === 'object' && !Array.isArray(source[key])) {
                out[key] = this.deepMerge(target[key] || {}, source[key]);
            } else {
                out[key] = source[key];
            }
        }
        return out;
    }
}

module.exports = Config;
