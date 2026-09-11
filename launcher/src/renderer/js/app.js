// ============================================
// Laura Launcher — Renderer logic
// ============================================

const $ = (s, ctx = document) => ctx.querySelector(s);
const $$ = (s, ctx = document) => Array.from(ctx.querySelectorAll(s));

// ============================================
// Helpers
// ============================================

// Привязка клика по id. Раньше эта функция использовалась, но не была
// определена — из-за ReferenceError падал весь DOMContentLoaded, и кнопки
// ИГРАТЬ / «Папка игры» и остальные контролы оставались без обработчиков.
function bindClick(id, handler) {
    const el = document.getElementById(id);
    if (!el) {
        console.warn('[laura] кнопка не найдена в разметке, клик не привязан:', id);
        return;
    }
    el.addEventListener('click', handler);
}

// Каждый блок инициализации оборачиваем в step(), чтобы ошибка в одном
// месте больше не отключала все остальные кнопки и контролы.
function step(name, fn) {
    try {
        fn();
    } catch (err) {
        console.error(`[laura] инициализация: «${name}» —`, err);
    }
}

// Кнопка «Папка игры» (и «Папка» в настройках): открывает папку инстанса.
// Папка создаётся автоматически, если её ещё нет.
async function onOpenGameFolder() {
    try {
        const result = await window.sakura.shell.openGameFolder();
        if (result && result.success) {
            toast(`Папка игры: ${result.path}`, 'success');
            updateInstanceHint(result.path);
        } else {
            const reason = (result && result.error) || 'неизвестная ошибка';
            toast(`Не удалось открыть папку игры: ${reason}`, 'error');
        }
    } catch (err) {
        toast(`Не удалось открыть папку игры: ${err.message}`, 'error');
    }
}

// ============================================
// State
// ============================================
const state = {
    config: null,
    currentTab: 'play',
    loader: 'fabric',
    authMode: 'offline'
};

// ============================================
// Init
// ============================================
document.addEventListener('DOMContentLoaded', async () => {
    // Загружаем конфиг
    try {
        state.config = await window.sakura.config.get();
        applyConfigToUI();
    } catch (err) {
        console.error('[laura] не удалось загрузить конфиг:', err);
    }

    step('управление окном', () => {
        $('#btn-minimize').onclick = () => window.sakura.window.minimize();
        $('#btn-maximize').onclick = () => window.sakura.window.maximize();
        $('#btn-close').onclick = () => window.sakura.window.close();
    });

    // Навигация
    step('навигация', () => {
        $$('.nav-item').forEach(btn => {
            btn.onclick = () => switchTab(btn.dataset.tab);
        });
    });

    // Play
    step('кнопка ИГРАТЬ', () => bindClick('btn-play', onPlayClick));

    // Папка игры — на главной и в настройках
    step('кнопки папки игры', () => {
        bindClick('btn-open-game-folder', onOpenGameFolder);
        bindClick('btn-open-instance', onOpenGameFolder);
        bindClick('instance-hint', onOpenGameFolder);
    });

    // Слайдеры RAM
    step('слайдеры RAM', () => {
        bindSlider('ram-min', 'ram-min-value', v => `${v} МБ`);
        bindSlider('ram-max', 'ram-max-value', v => `${v} МБ`);
    });

    // Авторизация — загрузчик зафиксирован на Fabric.
    step('режим авторизации', () => {
        bindSegmented('[data-auth]', val => { state.authMode = val; saveDebounced(); });
    });

    // Кнопка добавить мод
    step('кнопка добавления мода', () => {
        $('#btn-add-mod').onclick = () => {
            toast('Добавление модов появится в следующей версии', 'info');
        };
    });

    // Кнопка "Обзор" в настройках
    step('кнопки «Обзор»', () => {
        $$('[data-browse]').forEach(btn => {
            btn.onclick = () => onBrowse(btn.dataset.browse);
        });
    });

    // Поля ввода — сохранение
    step('поля настроек', () => {
        ['java-path', 'instance-path', 'client-jar-path', 'mc-version', 'nickname', 'res-width', 'res-height', 'fullscreen', 'autoupdate', 'after-launch', 'animations']
            .forEach(id => {
                const el = $('#' + id);
                if (!el) return;
                const ev = el.type === 'checkbox' ? 'change' : 'input';
                el.addEventListener(ev, saveDebounced);
            });
    });

    // Параллакс по движению мыши
    step('параллакс', bindParallax);

    // Если анимации выключены в конфиге
    if (state.config && state.config.ui && state.config.ui.animations === false) {
        document.body.classList.add('no-animations');
    }

    // Прогресс подготовки игры — статусы приходят из main-процесса,
    // чтобы было видно, что происходит после нажатия ИГРАТЬ.
    step('прогресс запуска', () => {
        if (window.sakura.minecraft.onProgress) {
            window.sakura.minecraft.onProgress(message => {
                const status = $('#play-status');
                if (status && message) status.textContent = message;
            });
        }
    });

    // Показываем, где лежит папка игры, и подтягиваем аватарки команды.
    step('путь к папке игры', () => { updateInstanceHint(); });
    step('аватарки «О нас»', () => { loadTeamAvatars(); });

    // Стартовое сообщение
    step('стартовый тост', () => toast('Laura Launcher запущен', 'success'));
});

// ============================================
// Instance path hint
// ============================================

// Показывает реальный путь к папке инстанса на главном экране
// (и подставляет его в placeholder настроек), чтобы было понятно,
// куда лаунчер ставит моды и где искать логи.
async function updateInstanceHint(forcePath) {
    const el = $('#instance-hint');
    let info = null;

    if (forcePath) {
        info = { path: forcePath };
    } else if (window.sakura.instance && window.sakura.instance.info) {
        try {
            info = await window.sakura.instance.info();
        } catch (err) {
            console.warn('[laura] не удалось получить путь инстанса:', err);
        }
    }
    if (!info || !info.path) return;

    if (el) {
        el.textContent = info.path;
        el.title = info.exists
            ? 'Открыть папку игры'
            : `Папка будет создана при первом запуске: ${info.path}`;
    }

    const input = $('#instance-path');
    if (input && !input.value) input.placeholder = info.path;
}

// ============================================
// Tab switching
// ============================================
function switchTab(tab) {
    state.currentTab = tab;
    $$('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === tab));
    $$('.nav-item').forEach(b => b.classList.toggle('active', b.dataset.tab === tab));
    try {
        $('.content').scrollTo({ top: 0, behavior: 'smooth' });
    } catch (_) {
        // Старые движки без Element.scrollTo — просто пропускаем прокрутку.
    }
}

// ============================================
// Play
// ============================================
async function onPlayClick() {
    const status = $('#play-status');
    const indicator = $('.status-indicator');
    const versionEl = $('#play-version');
    try {
        indicator?.classList.add('busy');
        indicator?.classList.remove('error');
        if (status) status.textContent = 'Подготовка...';
        if (versionEl && state.config) {
            versionEl.textContent = `${state.config.game.version} · ${state.config.game.loader}`;
        }

        // Сохраняем актуальные значения перед запуском
        await saveConfig();

        const result = await window.sakura.minecraft.launch({
            loader: 'fabric',
            authMode: state.authMode
        });

        if (result.success) {
            if (status) status.textContent = 'Игра запущена';
            toast(result.message || 'Laura Client запущен!', 'success');
            indicator?.classList.remove('busy');
            // Обновляем статистику
            setTimeout(refreshProfile, 200);
        } else {
            throw new Error(result.error);
        }
    } catch (err) {
        indicator?.classList.remove('busy');
        indicator?.classList.add('error');
        if (status) status.textContent = 'Ошибка запуска';
        toast('Не удалось запустить: ' + err.message, 'error');
    }
}

// ============================================
// Sliders
// ============================================
function bindSlider(inputId, valueId, formatter) {
    const input = $('#' + inputId);
    const value = $('#' + valueId);
    if (!input || !value) return;
    const update = () => { value.textContent = formatter(input.value); saveDebounced(); };
    input.addEventListener('input', update);
    update();
}

// ============================================
// Segmented control
// ============================================
function bindSegmented(selector, onChange) {
    $$(selector).forEach(btn => {
        btn.onclick = () => {
            const group = btn.parentElement;
            $$('button', group).forEach(b => b.classList.toggle('active', b === btn));
            const val = btn.dataset.loader || btn.dataset.auth;
            if (val) onChange(val);
        };
    });
}

// ============================================
// Browse folders
// ============================================
async function onBrowse(kind) {
    if (kind === 'clientJar') {
        const file = await window.sakura.dialog.selectFile();
        if (file) {
            $('#client-jar-path').value = file;
            saveDebounced();
        }
        return;
    }

    const folder = await window.sakura.dialog.selectFolder();
    if (!folder) return;
    if (kind === 'java') {
        // Путь к java.exe/java строит main-процесс: он знает платформу и
        // понимает и корень JDK, и выбранную папку bin.
        const javaPath = await window.sakura.dialog.selectJava();
        if (javaPath) {
            $('#java-path').value = javaPath;
            saveDebounced();
        }
        return;
    }
    if (kind === 'instance') {
        $('#instance-path').value = folder;
        saveDebounced();
    }
}

// ============================================
// Config <-> UI
// ============================================
function applyConfigToUI() {
    const c = state.config;
    if (!c) return;

    // Profile
    $('#nickname').value = c.profile.nickname;
    state.authMode = c.profile.authMode;
    $$('[data-auth]').forEach(b => b.classList.toggle('active', b.dataset.auth === c.profile.authMode));

    // Game
    $('#ram-min').value = c.game.ramMin;
    $('#ram-min-value').textContent = `${c.game.ramMin} МБ`;
    $('#ram-max').value = c.game.ramMax;
    $('#ram-max-value').textContent = `${c.game.ramMax} МБ`;
    $('#java-path').value = c.game.javaPath || '';
    $('#instance-path').value = c.game.instancePath || '';
    $('#client-jar-path').value = c.game.clientJarPath || '';
    $('#mc-version').value = c.game.version;
    state.loader = 'fabric';
    $$('[data-loader]').forEach(b => b.classList.toggle('active', b.dataset.loader === 'fabric'));
    $('#res-width').value = c.game.width;
    $('#res-height').value = c.game.height;
    $('#fullscreen').checked = c.game.fullscreen;
    $('#autoupdate').checked = c.game.autoUpdate;
    $('#after-launch').value = c.game.afterLaunch || 'stay';
    if (c.ui) $('#animations').checked = c.ui.animations !== false;

    // Play tab
    $('#hero-nickname').textContent = c.profile.nickname;
    $('#play-version').textContent = `${c.game.version} · ${c.game.loader}`;
    $('#profile-nick').textContent = c.profile.nickname;
    $('#profile-mode').textContent = c.profile.authMode === 'offline' ? 'Offline-режим' : 'Microsoft-аккаунт';

    // Stats
    if (c.lastPlayed) {
        const d = new Date(c.lastPlayed);
        $('#stat-last').textContent = d.toLocaleDateString('ru-RU');
    }
}

let saveTimer = null;
function saveDebounced() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(saveConfig, 200);
}

async function saveConfig() {
    state.config = await window.sakura.config.set({
        profile: {
            nickname: $('#nickname').value,
            authMode: state.authMode
        },
        game: {
            version: $('#mc-version').value,
            loader: 'fabric',
            fabricLoaderVersion: state.config?.game?.fabricLoaderVersion || '0.18.4',
            clientJarPath: $('#client-jar-path').value,
            ramMin: parseInt($('#ram-min').value, 10),
            ramMax: parseInt($('#ram-max').value, 10),
            javaPath: $('#java-path').value,
            instancePath: $('#instance-path').value,
            width: parseInt($('#res-width').value, 10) || 854,
            height: parseInt($('#res-height').value, 10) || 480,
            fullscreen: $('#fullscreen').checked,
            autoUpdate: $('#autoupdate').checked,
            afterLaunch: $('#after-launch').value
        },
        ui: {
            accent: '#ff2d8a',
            theme: 'laura',
            animations: $('#animations').checked
        }
    });
    if (!$('#animations').checked) document.body.classList.add('no-animations');
    else document.body.classList.remove('no-animations');
    // обновим ник в hero/profile
    const nick = $('#nickname').value;
    if (nick) {
        $('#hero-nickname').textContent = nick;
        $('#profile-nick').textContent = nick;
    }
    $('#profile-mode').textContent = state.authMode === 'offline' ? 'Offline-режим' : 'Microsoft-аккаунт';
}

// ============================================
// Parallax — фон реагирует на курсор
// ============================================
function bindParallax() {
    const layers = $$('.parallax-layer');
    if (!layers.length) return;
    let raf = null;
    document.addEventListener('mousemove', (e) => {
        if (raf) return;
        raf = requestAnimationFrame(() => {
            const x = (e.clientX / window.innerWidth - 0.5);
            const y = (e.clientY / window.innerHeight - 0.5);
            layers.forEach((layer, i) => {
                const depth = (i + 1) * 8;
                layer.style.transform = `translate(${x * depth}px, ${y * depth}px)`;
            });
            raf = null;
        });
    });
}

// ============================================
// Toast
// ============================================
function toast(message, kind = 'info') {
    const container = $('#toast-container');
    const el = document.createElement('div');
    el.className = `toast toast--${kind}`;
    el.textContent = message;
    container.appendChild(el);
    setTimeout(() => {
        el.style.animation = 'toast-out 0.3s ease-in forwards';
        setTimeout(() => el.remove(), 300);
    }, 3500);
}

// ============================================
// Profile stats refresh
// ============================================
function refreshProfile() {
    // Просто обновляем счётчик запусков локально
    const el = $('#stat-launches');
    if (el) {
        const cur = parseInt(el.textContent, 10) || 0;
        el.textContent = String(cur + 1);
    }
}

// ============================================
// Team avatars («О нас»)
// ============================================

// Аватарки подтягиваются с GitHub через main-процесс (с кэшем на диске),
// поэтому вкладка «О нас» работает и офлайн — показывается последний кэш,
// а если его нет, остаётся цветная заглушка.
async function loadTeamAvatars() {
    if (!window.sakura.about || typeof window.sakura.about.team !== 'function') return;

    let team = null;
    try {
        team = await window.sakura.about.team();
    } catch (err) {
        console.warn('[laura] не удалось получить данные команды:', err);
        return;
    }
    if (!Array.isArray(team)) return;

    for (const member of team) {
        if (!member || !member.login) continue;
        const card = $(`.member-card[data-login="${CSS.escape(member.login)}"]`);
        if (!card) continue;

        const avatarEl = $('.member-avatar', card);
        // Идемпотентно: если аватар уже вставлен, повторный вызов не дублирует.
        if (avatarEl && member.avatar && !$('.member-avatar-img', avatarEl)) {
            const img = document.createElement('img');
            img.className = 'member-avatar-img';
            img.alt = member.name || member.login;
            img.addEventListener('load', () => avatarEl.classList.add('has-photo'));
            img.addEventListener('error', () => img.remove());
            img.src = member.avatar;
            avatarEl.appendChild(img);
        }

        // Обновляем ссылку на GitHub из данных main-процесса
        const link = $('.member-github', card);
        if (link && member.github) link.href = member.github;
    }
}
