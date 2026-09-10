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
        selectFolder: () => ipcRenderer.invoke('dialog:selectFolder')
    },
    // Minecraft
    minecraft: {
        launch: (options) => ipcRenderer.invoke('minecraft:launch', options),
        versions: (type) => ipcRenderer.invoke('minecraft:versions', type)
    },
    // Утилиты
    shell: {
        openFolder: (folder) => ipcRenderer.send('shell:openFolder', folder)
    }
});
