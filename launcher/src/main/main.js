// Главный процесс Electron
const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const os = require('os');
const Config = require('./config');
const MinecraftLauncher = require('./launcher');
const TeamInfo = require('./team');

// Конфиг создаём лениво, только после app.ready: app.getPath('userData')
// до этого момента может бросить исключение и уронить весь лаунчер.
let config = null;
function getConfig() {
    if (!config) config = new Config();
    return config;
}

let mainWindow = null;

// Один экземпляр: повторный запуск просто фокусирует открытое окно.
const gotLock = app.requestSingleInstanceLock();
if (!gotLock) {
    app.quit();
} else {
    app.on('second-instance', () => {
        if (mainWindow) {
            if (mainWindow.isMinimized()) mainWindow.restore();
            mainWindow.focus();
        }
    });
}

// Непадающий лаунчер: вместо молчаливого закрытия показываем ошибку.
process.on('uncaughtException', error => {
    console.error('Uncaught exception:', error);
    try {
        dialog.showErrorBox(
            'Laura Launcher — ошибка',
            `Что-то пошло не так:\n${error && error.message ? error.message : error}`
        );
    } catch (_) { /* best effort */ }
});

function createWindow() {
    const preloadPath = path.join(__dirname, 'preload.js');
    if (!fs.existsSync(preloadPath)) {
        dialog.showErrorBox(
            'Laura Launcher — ошибка',
            `Не найден preload-скрипт:\n${preloadPath}\nПереустановите лаунчер.`
        );
        app.quit();
        return;
    }

    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        minWidth: 1024,
        minHeight: 700,
        frame: false,
        // Прозрачные окна нестабильны на Linux без композитора (чёрный экран),
        // поэтому там используем обычное непрозрачное окно.
        transparent: process.platform !== 'linux',
        backgroundColor: '#0a0a14',
        titleBarStyle: 'hidden',
        show: false,
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            preload: preloadPath
        }
    });

    mainWindow.once('ready-to-show', () => {
        mainWindow?.show();
    });

    // Если интерфейс не загрузился — показываем причину, а не чёрное окно.
    mainWindow.webContents.on('did-fail-load', (_event, errorCode, errorDescription) => {
        console.error('did-fail-load:', errorCode, errorDescription);
        try {
            if (mainWindow && !mainWindow.isVisible()) mainWindow.show();
            dialog.showErrorBox(
                'Laura Launcher — ошибка',
                `Не удалось загрузить интерфейс (${errorCode}):\n${errorDescription}`
            );
        } catch (_) { /* best effort */ }
    });

    mainWindow.webContents.on('render-process-gone', (_event, details) => {
        console.error('render-process-gone:', details);
        try {
            dialog.showErrorBox(
                'Laura Launcher — ошибка',
                `Интерфейс аварийно завершился (${details?.reason || 'unknown'}).\nПерезапустите лаунчер.`
            );
        } catch (_) { /* best effort */ }
    });

    mainWindow.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));

    // Внешние ссылки из интерфейса открываются в браузере, а не внутри лаунчера.
    mainWindow.webContents.setWindowOpenHandler(({ url }) => {
        const isDiscord = url === 'https://discord.com'
            || url.startsWith('https://discord.com/')
            || url.startsWith('https://discord.gg/');
        const isGithubProfile = url === 'https://github.com/Ironcarrier228'
            || url.startsWith('https://github.com/Ironcarrier228/')
            || url === 'https://github.com/bakaforlive'
            || url.startsWith('https://github.com/bakaforlive/');
        if (isDiscord || isGithubProfile) shell.openExternal(url);
        return { action: 'deny' };
    });
    // Блокируем только внешние навигации. Без проверки file:// этот хендлер
    // может отменить и первичную загрузку интерфейса — будет чёрный экран.
    mainWindow.webContents.on('will-navigate', (event, url) => {
        if (typeof url === 'string' && url.startsWith('file:')) return;
        event.preventDefault();
    });

    if (process.argv.includes('--dev')) {
        mainWindow.webContents.openDevTools({ mode: 'detach' });
    }

    mainWindow.on('closed', () => {
        mainWindow = null;
    });
}

// IPC: управление окном
ipcMain.on('window:minimize', () => mainWindow?.minimize());
ipcMain.on('window:maximize', () => {
    if (mainWindow?.isMaximized()) mainWindow.unmaximize();
    else mainWindow?.maximize();
});
ipcMain.on('window:close', () => mainWindow?.close());

// IPC: конфиг
ipcMain.handle('config:get', () => getConfig().get());
ipcMain.handle('config:set', (_, data) => {
    getConfig().set(data);
    return getConfig().get();
});

// IPC: диалоги
ipcMain.handle('dialog:selectFolder', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
        properties: ['openDirectory']
    });
    return result.canceled ? null : result.filePaths[0];
});

ipcMain.handle('dialog:selectFile', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
        properties: ['openFile'],
        filters: [{ name: 'Fabric client JAR', extensions: ['jar'] }]
    });
    return result.canceled ? null : result.filePaths[0];
});

// IPC: выбор папки Java — путь к java.exe/java строит main-процесс,
// он знает платформу и умеет разбирать и корень JDK, и саму папку bin.
ipcMain.handle('dialog:selectJava', async () => {
    const result = await dialog.showOpenDialog(mainWindow, {
        title: 'Выберите папку JDK 21 (или её папку bin)',
        properties: ['openDirectory']
    });
    if (result.canceled || !result.filePaths.length) return null;
    return MinecraftLauncher.resolveJavaExecutable(result.filePaths[0]);
});

// IPC: запуск Minecraft
ipcMain.handle('minecraft:launch', async (_, options) => {
    const launcher = new MinecraftLauncher(getConfig());
    // Прогресс подготовки (Java, JAR, загрузка файлов) — в интерфейс,
    // чтобы после нажатия ИГРАТЬ было видно, что происходит.
    launcher.onProgress = message => {
        try {
            if (mainWindow && !mainWindow.isDestroyed()) {
                mainWindow.webContents.send('minecraft:progress', String(message));
            }
        } catch (_) { /* прогресс не должен ломать запуск */ }
    };
    try {
        const result = await launcher.launch(options);
        // Поведение окна после того, как Java действительно стартовала.
        // Важно делать это только после успешного spawn: при ошибке лаунчер
        // остаётся видимым и показывает пользователю причину.
        const afterLaunch = getConfig().get().game.afterLaunch;
        if (mainWindow && !mainWindow.isDestroyed()) {
            if (afterLaunch === 'minimize') mainWindow.minimize();
            else if (afterLaunch === 'close') mainWindow.close();
        }
        return { success: true, ...result };
    } catch (err) {
        return { success: false, error: err.message };
    }
});

// IPC: получение списка версий
ipcMain.handle('minecraft:versions', async (_, type) => {
    return MinecraftLauncher.fetchVersions(type);
});

// IPC: открыть произвольную папку (fire-and-forget, для совместимости)
ipcMain.on('shell:openFolder', (_, folder) => {
    try {
        const target = folder && String(folder).trim()
            ? path.resolve(String(folder).trim())
            : MinecraftLauncher.resolveInstancePath(getConfig().get());
        fs.mkdirSync(target, { recursive: true });
        shell.openPath(target);
    } catch (err) {
        console.error('shell:openFolder failed:', err);
    }
});

// IPC: кнопка «Папка игры» — открывает инстанс (создаёт, если его ещё нет)
// и возвращает результат, чтобы интерфейс мог показать тост.
ipcMain.handle('shell:openGameFolder', async () => {
    try {
        const instancePath = MinecraftLauncher.resolveInstancePath(getConfig().get());
        fs.mkdirSync(instancePath, { recursive: true });
        const error = await shell.openPath(instancePath);
        if (error) return { success: false, path: instancePath, error };
        return { success: true, path: instancePath };
    } catch (err) {
        return { success: false, error: err.message };
    }
});

// IPC: информация о папке инстанса — путь и существует ли она.
// Нужна, чтобы интерфейс показывал, где именно лаунчер держит игру.
ipcMain.handle('instance:info', () => {
    try {
        const instancePath = MinecraftLauncher.resolveInstancePath(getConfig().get());
        return { path: instancePath, exists: fs.existsSync(instancePath) };
    } catch (err) {
        return { path: '', exists: false, error: err.message };
    }
});

// IPC: данные команды для «О нас» — аватарки с GitHub (с кэшем на диске).
ipcMain.handle('about:team', async () => {
    try {
        let cacheDirectory;
        try {
            cacheDirectory = path.join(app.getPath('userData'), 'team-cache');
        } catch (_) {
            cacheDirectory = path.join(os.homedir(), '.laura-launcher', 'team-cache');
        }
        return await new TeamInfo(cacheDirectory).getTeam();
    } catch (err) {
        console.error('about:team failed:', err);
        return [];
    }
});

app.whenReady()
    .then(() => {
        getConfig(); // инициализация конфига только после ready
        createWindow();
    })
    .catch(error => {
        console.error('app.whenReady failed:', error);
        try {
            dialog.showErrorBox('Laura Launcher — ошибка', `Не удалось запустить:\n${error.message}`);
        } catch (_) { /* best effort */ }
    });

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
