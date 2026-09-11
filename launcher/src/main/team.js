// Данные команды для вкладки «О нас».
// Аватарки подтягиваются с GitHub (сначала API, затем .png-fallback)
// и кэшируются на диске: вкладка работает офлайн и не грузит GitHub
// при каждом открытии лаунчера.
const fs = require('fs');
const path = require('path');
const http = require('http');
const https = require('https');
const { pathToFileURL } = require('url');

const CACHE_TTL_MS = 24 * 60 * 60 * 1000; // обновляем аватарки раз в сутки
const REQUEST_TIMEOUT_MS = 15_000;
const MAX_REDIRECTS = 5;
const USER_AGENT = 'Laura-Launcher/1.0';

const GITHUB_API = 'https://api.github.com/users/';
const GITHUB_AVATAR_FALLBACK = 'https://github.com/';

// Состав команды. bio/role можно править здесь — разметка «О нас»
// подхватывает логин для поиска карточки и ссылку на GitHub.
const TEAM = [
    {
        login: 'Ironcarrier228',
        name: 'Ironcarrier',
        role: 'Разработка клиента',
        bio: 'Создание Laura Client и архитектура проекта.',
        github: 'https://github.com/Ironcarrier228'
    },
    {
        login: 'bakaforlive',
        name: 'Fen',
        role: 'Разработка и дизайн',
        bio: 'Развитие клиента, интерфейсы и новые возможности.',
        github: 'https://github.com/bakaforlive'
    }
];

class TeamInfo {
    constructor(cacheDirectory) {
        this.cacheDirectory = cacheDirectory;
    }

    avatarCachePath(login) {
        // login ограничиваем безопасным набором символов — защита от path traversal
        const safe = String(login).replace(/[^A-Za-z0-9-_.]/g, '');
        return path.join(this.cacheDirectory, `${safe || 'user'}.png`);
    }

    static isCacheFresh(filePath) {
        try {
            const stat = fs.statSync(filePath);
            return stat.isFile() && stat.size > 0 && (Date.now() - stat.mtimeMs) < CACHE_TTL_MS;
        } catch (_) {
            return false;
        }
    }

    static request(url, redirects = 0) {
        if (redirects > MAX_REDIRECTS) {
            return Promise.reject(new Error('Слишком много перенаправлений.'));
        }
        return new Promise((resolve, reject) => {
            let parsed;
            try {
                parsed = new URL(url);
            } catch (error) {
                reject(error);
                return;
            }
            const transport = parsed.protocol === 'http:' ? http : https;
            const request = transport.get(parsed, {
                headers: {
                    'User-Agent': USER_AGENT,
                    'Accept': 'application/vnd.github+json, image/*'
                }
            }, response => {
                const status = response.statusCode || 0;
                if ([301, 302, 303, 307, 308].includes(status) && response.headers.location) {
                    response.resume();
                    TeamInfo.request(new URL(response.headers.location, parsed).toString(), redirects + 1)
                        .then(resolve)
                        .catch(reject);
                    return;
                }
                if (status < 200 || status >= 300) {
                    response.resume();
                    reject(new Error(`HTTP ${status} для ${url}`));
                    return;
                }
                const chunks = [];
                response.on('data', chunk => chunks.push(chunk));
                response.on('end', () => resolve(Buffer.concat(chunks)));
                response.on('error', reject);
            });
            request.setTimeout(REQUEST_TIMEOUT_MS, () => {
                request.destroy(new Error(`Истекло время ожидания: ${url}`));
            });
            request.on('error', reject);
        });
    }

    // Скачивает аватар в память. Сначала GitHub API (отдаёт актуальный
    // avatar_url), при недоступности API — прямой .png по логину.
    static async fetchAvatarBuffer(login) {
        try {
            const payload = JSON.parse(
                (await TeamInfo.request(`${GITHUB_API}${encodeURIComponent(login)}`)).toString('utf8')
            );
            if (payload && typeof payload.avatar_url === 'string' && payload.avatar_url) {
                const buffer = await TeamInfo.request(payload.avatar_url);
                if (buffer.length > 0) return buffer;
            }
        } catch (_) {
            // API мог быть недоступен — пробуем fallback ниже.
        }
        return await TeamInfo.request(`${GITHUB_AVATAR_FALLBACK}${encodeURIComponent(login)}.png`);
    }

    // Возвращает file:// URL аватара или null, если ни сети, ни кэша нет.
    async resolveAvatar(login) {
        const cachePath = this.avatarCachePath(login);

        if (!TeamInfo.isCacheFresh(cachePath)) {
            try {
                const buffer = await TeamInfo.fetchAvatarBuffer(login);
                if (buffer && buffer.length > 0) {
                    fs.mkdirSync(this.cacheDirectory, { recursive: true });
                    // Пишем атомарно: .part -> rename, чтобы не остался битый файл.
                    const temporary = `${cachePath}.part`;
                    fs.writeFileSync(temporary, buffer);
                    fs.renameSync(temporary, cachePath);
                }
            } catch (err) {
                // Нет сети/GitHub недоступен — молча используем кэш или заглушку.
                console.warn('[laura] аватар с GitHub не получен:', login, err.message);
            }
        }

        try {
            if (fs.existsSync(cachePath) && fs.statSync(cachePath).size > 0) {
                return pathToFileURL(cachePath).toString();
            }
        } catch (_) { /* проверка кэша не должна ронять вкладку */ }
        return null;
    }

    async getTeam() {
        const members = [];
        for (const member of TEAM) {
            const avatar = await this.resolveAvatar(member.login);
            members.push({ ...member, avatar });
        }
        return members;
    }
}

TeamInfo.TEAM = TEAM;

module.exports = TeamInfo;
