; ============================================================
;  Laura Client - Windows installer (Inno Setup 6)
;
;  Before compiling:
;    1. Build the client:   gradlew build   (JAR goes to build\libs\)
;    2. Install Inno Setup 6: https://jrsoftware.org/isdl.php
;    3. Compile this script (File -> Compile) in Inno Setup IDE.
;
;  Result: Output\Laura-Client-Setup.exe
; ============================================================

#define MyAppName "Laura Client"
#define MyAppVersion "1.21.4"
#define MyAppPublisher "Ironcarrier228"
#define MyAppURL "https://github.com/Ironcarrier228/Laura-Client-1.21.4"

[Setup]
AppId={{7C9E4B21-5A6D-4E8F-9B3C-2D4E6F8A0C1B}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppVerName={#MyAppName} {#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}
DefaultDirName={localappdata}\Laura Client
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
PrivilegesRequired=lowest
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
OutputDir=Output
OutputBaseFilename=Laura-Client-Setup
SetupIconFile=icon.ico
UninstallDisplayIcon={app}\icon.ico
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ShowLanguageDialog=no

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"
; Russian: drop ru.isl into Inno Setup's Languages folder, then uncomment:
; Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"

[Tasks]
Name: "desktopicon"; Description: "Create a &desktop shortcut"; GroupDescription: "Additional icons:"; Flags: unchecked

[Files]
Source: "laura-launch.bat"; DestDir: "{app}"; Flags: ignoreversion
Source: "LauraLauncher.ps1"; DestDir: "{app}"; Flags: ignoreversion
Source: "icon.ico"; DestDir: "{app}"; Flags: ignoreversion
; Client JAR (build it first: gradlew build)
Source: "..\build\libs\laura-client-*.jar"; DestDir: "{app}"; Flags: ignoreversion

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "laura-launch.bat"
Name: "{autodesktop}\{#MyAppName}"; Filename: "laura-launch.bat"; Tasks: desktopicon

[Run]
Filename: "laura-launch.bat"; WorkingDir: "{app}"; Flags: nowait postinstall skipifsilent

[UninstallDelete]
Type: filesandordirs; Name: "{localappdata}\Laura Client\instance"
Type: filesandordirs; Name: "{localappdata}\Laura Client\scripts"
