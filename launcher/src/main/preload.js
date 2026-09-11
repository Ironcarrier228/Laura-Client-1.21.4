// Мост между рендером и main процессом
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('sakura', {
    // Управление окном
    window: {
        minimize: () => ipcRenderer.send('window:minimize'),
        maximize: () => ipcRenderer.send('window:maximize'),
        close: () => ipcRenderer.send('window:close')
    },
    // Конфигурация
    config: {
        get: () => ipcRenderer.invoke('config:get'),
        set: (data) => ipcRenderer.invoke('config:set', data)
    },
    // Системные диалоги
    dialog: {
        selectFolder: () => ipcRenderer.invoke('dialog:selectFolder'),
        selectFile: () => ipcRenderer.invoke('dialog:selectFile'),
        selectJava: () => ipcRenderer.invoke('dialog:selectJava')
    },
    // Minecraft
    minecraft: {
        launch: (options) => ipcRenderer.invoke('minecraft:launch', options),
        versions: (type) => ipcRenderer.invoke('minecraft:versions', type),
        // Этапы подготовки игры (Java, JAR, загрузка файлов, запуск)
        onProgress: (callback) => {
            ipcRenderer.on('minecraft:progress', (_, message) => {
                if (typeof callback === 'function') callback(message);
            });
        }
    },
    // Папка инстанса: путь и существование
    instance: {
        info: () => ipcRenderer.invoke('instance:info')
    },
    // Вкладка «О нас»: состав команды и аватарки с GitHub
    about: {
        team: () => ipcRenderer.invoke('about:team')
    },
    // Утилиты
    shell: {
        openFolder: (folder) => ipcRenderer.send('shell:openFolder', folder),
        // Кнопка «Папка игры»: открывает инстанс и возвращает результат.
        openGameFolder: () => ipcRenderer.invoke('shell:openGameFolder')
    }
});
