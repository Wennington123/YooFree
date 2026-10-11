; YooFree - instalador Windows (Inno Setup 6.3+)
;
; Compilar (de dentro de desktop/packaging):
;   ISCC.exe /DArch=x64   yoofree.iss
;   ISCC.exe /DArch=arm64 yoofree.iss
;
; Espera o PyInstaller ja ter gerado desktop/dist/YooFree/YooFree.exe

#ifndef Arch
  #define Arch "x64"
#endif
#define AppName "YooFree"
#define AppVersion "0.1"
#define AppExeName "YooFree.exe"

#if Arch == "arm64"
  #define ArchAllowed "arm64"
  #define ArchMode "arm64"
#else
  #define ArchAllowed "x64compatible"
  #define ArchMode "x64compatible"
#endif

[Setup]
AppId={{8F3A6C1E-7B24-4E6D-9C51-2A6E4C7B9F01}
AppName={#AppName}
AppVersion={#AppVersion}
AppVerName={#AppName} {#AppVersion}
AppPublisher=YooFree
AppPublisherURL=https://github.com/Wennington123/YooFree
AppSupportURL=https://github.com/Wennington123/YooFree/issues
DefaultDirName={autopf}\{#AppName}
DefaultGroupName={#AppName}
DisableProgramGroupPage=yes
UninstallDisplayIcon={app}\{#AppExeName}
UninstallDisplayName={#AppName} {#AppVersion}
OutputDir=..\dist_installer
OutputBaseFilename=YooFree-Setup-{#Arch}
SetupIconFile=..\..\assets\icon.ico
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
PrivilegesRequiredOverridesAllowed=dialog
ArchitecturesAllowed={#ArchAllowed}
ArchitecturesInstallIn64BitMode={#ArchMode}
LicenseFile=..\..\LICENSE

[Languages]
Name: "brazilianportuguese"; MessagesFile: "compiler:Languages\BrazilianPortuguese.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "Criar atalho na area de trabalho"; GroupDescription: "Atalhos:"; Flags: checkedonce

[Files]
Source: "..\dist\YooFree\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#AppName}"; Filename: "{app}\{#AppExeName}"
Name: "{group}\Desinstalar {#AppName}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#AppName}"; Filename: "{app}\{#AppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#AppExeName}"; Description: "Abrir o {#AppName} agora"; Flags: nowait postinstall skipifsilent
