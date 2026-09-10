// Главный процесс Electron
const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const Config = require('./config');
const MinecraftLauncher = require('./launcher');

const config = new Config();

let mainWindow = null;

function createWindow() {
    mainWindow = new BrowserWindow({
        width: 1280,
        height: 800,
        minWidth: 1024,
        minHeight: 700,
        frame: false,
        transparent: true,
        backgroundColor: '#0a0a14',
        titleBarStyle: 'hidden',
        webPreferences: {
            nodeIntegration: false,
            contextIsolation: true,
            preload: path.join(__dirname, 'preload.js')
        }
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
    mainWindow.webContents.on('will-navigate', event => event.preventDefault());

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
ipcMain.handle('config:get', () => config.get());
ipcMain.handle('config:set', (_, data) => {
    config.set(data);
    return config.get();
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

// IPC: запуск Minecraft
ipcMain.handle('minecraft:launch', async (_, options) => {
    const launcher = new MinecraftLauncher(config.get());
    try {
        const result = await launcher.launch(options);
        return { success: true, ...result };
    } catch (err) {
        return { success: false, error: err.message };
    }
});

// IPC: получение списка версий
ipcMain.handle('minecraft:versions', async (_, type) => {
    return MinecraftLauncher.fetchVersions(type);
});

// IPC: открыть папку
ipcMain.on('shell:openFolder', (_, folder) => {
    if (fs.existsSync(folder)) shell.openPath(folder);
});

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
    if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
