#Requires -Version 5.1
<#
.SYNOPSIS
Opt-in tests of real Windows picker/console windows. Run after mvnw.cmd verify.
.DESCRIPTION
Opens and closes only windows belonging to this run. Do not type into the test
windows. Requires an interactive Windows desktop. Window-close probes require
a Win32 console host; Windows Terminal pseudoconsole hosts are reported as skipped.
Exit code 2 means partial coverage because that host cannot safely be closed.
Every case and helper has a deadline. No keyboard focus or global SendKeys is used.
.EXAMPLE
powershell -NoProfile -ExecutionPolicy Bypass -File tools/windows-smoke.ps1
.EXAMPLE
powershell -NoProfile -ExecutionPolicy Bypass -File tools/windows-smoke.ps1 -UncDirectory '\\server\share\existing'
#>
[CmdletBinding()]
param(
    [string]$Jar,
    [string]$Java = 'java.exe',
    [ValidateRange(10, 120)][int]$TimeoutSeconds = 30,
    [string]$UncDirectory
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
if ($env:OS -ne 'Windows_NT') { throw 'This opt-in suite requires Windows.' }
if (-not [Environment]::UserInteractive) { throw 'An interactive Windows desktop is required.' }
if (-not $Jar) { $Jar = Join-Path $PSScriptRoot '..\target\Lexor_Project-1.0-SNAPSHOT.jar' }
$Jar = (Resolve-Path -LiteralPath $Jar).Path
$Java = @(Get-Command $Java -CommandType Application)[0].Source
$runName = 'lexor-smoke-' + [Guid]::NewGuid().ToString('N')
$runParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
$runRoot = [IO.Path]::GetFullPath((Join-Path $runParent $runName))
$null = New-Item -ItemType Directory -Path $runRoot
$ownedRoots = @(@{ Path = $runRoot; Parent = $runParent; Name = $runName })
$script:currentCase = $null
$script:canCloseWindow = $null
$script:passed = 0
$script:skipped = 0
$utf8 = New-Object System.Text.UTF8Encoding($false)

# A separate helper attaches to the owned console without detaching the caller's
# PowerShell from its console. All handles are opened explicitly after attachment.
$helperSource = @'
using System;
using System.ComponentModel;
using System.Runtime.InteropServices;
using System.Text;
using Microsoft.Win32.SafeHandles;
public static class LexorConsoleProbe {
    [StructLayout(LayoutKind.Sequential)] struct COORD { public short X, Y; }
    [StructLayout(LayoutKind.Sequential)] struct RECT { public short Left, Top, Right, Bottom; }
    [StructLayout(LayoutKind.Sequential)] struct INFO {
        public COORD Size, Cursor; public ushort Attributes; public RECT Window; public COORD Maximum;
    }
    [StructLayout(LayoutKind.Explicit, CharSet=CharSet.Unicode, Size=20)] struct INPUT {
        [FieldOffset(0)] public ushort Type;
        [FieldOffset(4)] public int Down;
        [FieldOffset(8)] public ushort Repeat;
        [FieldOffset(10)] public ushort VirtualKey;
        [FieldOffset(14)] public char Character;
    }
    delegate bool EnumCallback(IntPtr window, IntPtr data);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool AttachConsole(uint pid);
    [DllImport("kernel32.dll")] static extern bool FreeConsole();
    [DllImport("kernel32.dll")] static extern IntPtr GetConsoleWindow();
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    static extern SafeFileHandle CreateFile(string name, uint access, uint share, IntPtr security, uint mode, uint flags, IntPtr template);
    [DllImport("kernel32.dll", SetLastError=true)] static extern bool GetConsoleScreenBufferInfo(SafeFileHandle handle, out INFO info);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    static extern bool ReadConsoleOutputCharacter(SafeFileHandle handle, StringBuilder text, uint count, COORD origin, out uint read);
    [DllImport("kernel32.dll", CharSet=CharSet.Unicode, SetLastError=true)]
    static extern bool WriteConsoleInput(SafeFileHandle handle, INPUT[] records, uint count, out uint written);
    [DllImport("user32.dll")] static extern bool EnumWindows(EnumCallback callback, IntPtr data);
    [DllImport("user32.dll")] static extern bool IsWindowVisible(IntPtr window);
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint pid);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr window, StringBuilder title, int count);
    [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetClassName(IntPtr window, StringBuilder name, int count);
    [DllImport("user32.dll", SetLastError=true)] static extern bool PostMessage(IntPtr window, uint message, IntPtr wparam, IntPtr lparam);
    static void Check(bool ok) { if (!ok) throw new Win32Exception(Marshal.GetLastWin32Error()); }
    static bool NativeWindow(IntPtr window) {
        var name = new StringBuilder(256); GetClassName(window, name, name.Capacity);
        uint owner; GetWindowThreadProcessId(window, out owner);
        string process = owner == 0 ? "" : System.Diagnostics.Process.GetProcessById((int)owner).ProcessName;
        return window != IntPtr.Zero && IsWindowVisible(window)
            && name.ToString() == "ConsoleWindowClass" && process.Equals("conhost", StringComparison.OrdinalIgnoreCase);
    }
    static string WindowDescription(IntPtr window) {
        var name = new StringBuilder(256); GetClassName(window, name, name.Capacity);
        uint owner; GetWindowThreadProcessId(window, out owner);
        string process = owner == 0 ? "none" : System.Diagnostics.Process.GetProcessById((int)owner).ProcessName;
        return "HWND=" + window + "; class=" + name + "; visible=" + IsWindowVisible(window)
            + "; owner=" + process + " (PID " + owner + ")";
    }
    public static int Main(string[] args) {
        try {
            string operation = args[0]; uint pid = uint.Parse(args[1]);
            if (operation.StartsWith("picker-")) {
                IntPtr found = IntPtr.Zero;
                EnumWindows(delegate(IntPtr window, IntPtr data) {
                    uint owner; GetWindowThreadProcessId(window, out owner);
                    if (owner == pid) {
                        var title = new StringBuilder(512); GetWindowText(window, title, title.Capacity);
                        if (title.ToString() == "Open LEXOR script") found = window;
                    }
                    return true;
                }, IntPtr.Zero);
                if (found != IntPtr.Zero) {
                    if (operation == "picker-close") Check(PostMessage(found, 0x0010, IntPtr.Zero, IntPtr.Zero));
                    Console.Write("found");
                }
                return 0;
            }
            FreeConsole();
            Check(AttachConsole(pid));
            string result = "";
            try {
                if (operation == "read") {
                    using (var handle = CreateFile("CONOUT$", 0x80000000, 3, IntPtr.Zero, 3, 0, IntPtr.Zero)) {
                        INFO info; Check(GetConsoleScreenBufferInfo(handle, out info));
                        short top = (short)Math.Max(0, info.Cursor.Y - 200);
                        int count = info.Size.X * (info.Cursor.Y - top + 1);
                        var text = new StringBuilder(count); uint read;
                        Check(ReadConsoleOutputCharacter(handle, text, (uint)count, new COORD { X=0, Y=top }, out read));
                        result = Convert.ToBase64String(Encoding.UTF8.GetBytes(text.ToString()));
                    }
                } else if (operation == "write") {
                    string text = Encoding.UTF8.GetString(Convert.FromBase64String(args[2]));
                    using (var handle = CreateFile("CONIN$", 0xC0000000, 3, IntPtr.Zero, 3, 0, IntPtr.Zero)) {
                        var records = new INPUT[text.Length * 2];
                        for (int i = 0; i < text.Length; i++) {
                            records[i*2] = new INPUT { Type=1, Down=1, Repeat=1,
                                Character=text[i], VirtualKey=(ushort)(text[i]=='\r' ? 13 : 0) };
                            records[i*2+1] = records[i*2]; records[i*2+1].Down=0;
                        }
                        uint written; Check(WriteConsoleInput(handle, records, (uint)records.Length, out written));
                        if (written != records.Length) throw new Exception("Incomplete console input write");
                    }
                } else if (operation == "window-kind") {
                    IntPtr window = GetConsoleWindow();
                    result = NativeWindow(window) ? "win32" : "pseudoconsole";
                } else if (operation == "window-description") {
                    result = WindowDescription(GetConsoleWindow());
                } else if (operation == "close") {
                    IntPtr window = GetConsoleWindow();
                    if (!NativeWindow(window))
                        throw new Exception("A visible Win32 console host is required for window-close probes");
                    FreeConsole();
                    Check(PostMessage(window, 0x0112, new IntPtr(0xF060), IntPtr.Zero));
                } else throw new Exception("Unknown probe operation");
            } finally { FreeConsole(); }
            Console.Write(result);
            return 0;
        } catch (Exception error) { Console.Error.WriteLine(error.Message); return 1; }
    }
}
'@

function Quote-Argument([string]$Value) {
    # Windows CommandLineToArgvW quoting, including trailing backslashes.
    '"' + [regex]::Replace([regex]::Replace($Value, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1') + '"'
}

function Start-OwnedProcess([string]$Executable, [string[]]$Arguments, [string]$WorkingDirectory) {
    $info = New-Object Diagnostics.ProcessStartInfo
    $info.FileName = $Executable
    $info.Arguments = ($Arguments | ForEach-Object { Quote-Argument $_ }) -join ' '
    $info.WorkingDirectory = $WorkingDirectory
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $process = New-Object Diagnostics.Process
    $process.StartInfo = $info
    $null = $process.Start()
    $process
}

function Invoke-Probe([string]$Operation, [int]$Owner, [string]$Text = '') {
    $target = Get-Process -Id $Owner -ErrorAction SilentlyContinue
    if (-not $script:currentCase.Owned.ContainsKey($Owner) -or $null -eq $target -or
        $target.StartTime.ToUniversalTime().Ticks -ne $script:currentCase.Owned[$Owner]) {
        if ($Operation -eq 'picker-find') { return '' }
        throw 'Refusing console access: the owned process has exited or its PID was reused.'
    }
    $arguments = @($Operation, [string]$Owner)
    if ($Operation -eq 'write') { $arguments += [Convert]::ToBase64String($utf8.GetBytes($Text)) }
    $probe = Start-OwnedProcess $script:helper $arguments $runRoot
    try {
        $output = $probe.StandardOutput.ReadToEndAsync()
        $errorOutput = $probe.StandardError.ReadToEndAsync()
        if (-not $probe.WaitForExit(5000)) { throw "Console helper timed out: $Operation" }
        if ($probe.ExitCode -ne 0) { throw "Console helper failed ($Operation): $($errorOutput.Result)" }
        if ($Operation -eq 'read') { return $utf8.GetString([Convert]::FromBase64String($output.Result)) }
        $output.Result
    } finally {
        if (-not $probe.HasExited) { $probe.Kill(); $null = $probe.WaitForExit(5000) }
        $probe.Dispose()
    }
}

function Remember-Process([int]$ProcessId) {
    $process = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($null -ne $process) { $script:currentCase.Owned[$ProcessId] = $process.StartTime.ToUniversalTime().Ticks }
}

function Capture-Descendants {
    $processes = @(Get-CimInstance Win32_Process)
    do {
        $added = $false
        foreach ($process in $processes) {
            # Console hosts may delegate to a shared Windows Terminal process.
            # Track only the application's JVMs and command shells, never that host.
            if ($process.Name -notin @('java.exe', 'javaw.exe', 'cmd.exe')) { continue }
            $id = [int]$process.ProcessId
            if ($script:currentCase.Owned.ContainsKey([int]$process.ParentProcessId) -and
                -not $script:currentCase.Owned.ContainsKey($id)) {
                $parent = Get-Process -Id $process.ParentProcessId -ErrorAction SilentlyContinue
                $child = Get-Process -Id $id -ErrorAction SilentlyContinue
                if ($null -ne $parent -and $parent.StartTime.ToUniversalTime().Ticks -eq
                    $script:currentCase.Owned[[int]$process.ParentProcessId] -and $null -ne $child -and
                    $child.StartTime.ToUniversalTime().Ticks -ge $parent.StartTime.ToUniversalTime().Ticks) {
                    Remember-Process $id
                    $added = $script:currentCase.Owned.ContainsKey($id) -or $added
                }
            }
        }
    } while ($added)
}

function Wait-For([string]$Description, [scriptblock]$Condition) {
    while ([DateTime]::UtcNow -lt $script:currentCase.Deadline) {
        Capture-Descendants
        $value = & $Condition
        if ($null -ne $value -and $value -ne $false -and $value -ne '') { return $value }
        Start-Sleep -Milliseconds 100
    }
    throw "Timed out: $Description ($($script:currentCase.Name))"
}

function Assert-Equal($Expected, $Actual, [string]$Description) {
    if ($Expected -ne $Actual) { throw "$Description`: expected '$Expected', got '$Actual'" }
}

function Write-Script([string]$Path, [string]$Body) {
    [IO.File]::WriteAllText($Path, "SCRIPT AREA`nSTART SCRIPT`n$Body`nEND SCRIPT`n", $utf8)
}

function Start-Case([string]$Name, [string[]]$Arguments, [string]$WorkingDirectory, [string]$CaseJar = $Jar) {
    $caseRoot = Join-Path $runRoot $Name
    $null = New-Item -ItemType Directory -Path $caseRoot
    $script:currentCase = @{
        Name = $Name; Root = $caseRoot; Owned = @{}; Session = $null; Launcher = $null
        Deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    }
    $launchArgs = @('-Djava.awt.headless=false', "-Djava.io.tmpdir=$caseRoot", '-jar', $CaseJar) + $Arguments
    $launcher = Start-OwnedProcess $Java $launchArgs $WorkingDirectory
    $script:currentCase.Launcher = $launcher
    $script:currentCase.Output = $launcher.StandardOutput.ReadToEndAsync()
    $script:currentCase.Error = $launcher.StandardError.ReadToEndAsync()
    Remember-Process $launcher.Id
}

function Find-Session {
    $session = Wait-For 'owned session startup' {
        $started = @(Get-ChildItem -LiteralPath $script:currentCase.Root -Filter session.started -Recurse -File)
        if ($started.Count -eq 1 -and $started[0].Length -gt 0) { $started[0].DirectoryName }
        elseif ($script:currentCase.Launcher.HasExited) { throw "Launcher exited: $($script:currentCase.Error.Result)" }
    }
    $script:currentCase.Session = [string]$session
    $script:currentCase.SessionPid = [int][IO.File]::ReadAllText((Join-Path $session 'session.started'))
    $sessionProcess = Get-CimInstance Win32_Process -Filter "ProcessId=$($script:currentCase.SessionPid)"
    if ($null -eq $sessionProcess -or -not $sessionProcess.CommandLine.Contains((Join-Path $session 'java.args'))) {
        throw 'Session PID does not belong to the test argument file.'
    }
    Remember-Process $script:currentCase.SessionPid
}

function Wait-Completion([int]$ExpectedStatus, [string]$ExpectedText) {
    $null = Wait-For 'published completion status' {
        $file = Join-Path $script:currentCase.Session 'session.exit'
        if (Test-Path -LiteralPath $file) { 'published' }
    }
    $published = [int][IO.File]::ReadAllText((Join-Path $script:currentCase.Session 'session.exit'))
    if ($published -ne $ExpectedStatus) {
        $diagnostic = Invoke-Probe 'read' $script:currentCase.SessionPid
        throw "Published status: expected '$ExpectedStatus', got '$published'. Console: $diagnostic"
    }
    $screen = Wait-For 'visible completion prompt' {
        $text = Invoke-Probe 'read' $script:currentCase.SessionPid
        if ($text.Contains('Press Enter to close...')) { $text }
    }
    if (-not $screen.Contains($ExpectedText)) { throw "Expected console output '$ExpectedText'; got: $screen" }
    if (-not $screen.Contains("LEXOR finished with exit code $ExpectedStatus.")) { throw 'Completion status was not displayed.' }
}

function Assert-ExitAndCleanup([Nullable[int]]$ExpectedStatus, [bool]$ExpectLeftover = $false) {
    $null = Wait-For 'launcher exit' { $script:currentCase.Launcher.HasExited }
    $status = $script:currentCase.Launcher.ExitCode
    if ($null -ne $ExpectedStatus) { Assert-Equal $ExpectedStatus $status 'Launcher status' }
    elseif ($status -eq 0) { throw 'Closing a running script must report interruption, not success.' }
    $null = Wait-For 'owned process cleanup' {
        $alive = @($script:currentCase.Owned.Keys | Where-Object {
            $process = Get-Process -Id $_ -ErrorAction SilentlyContinue
            $null -ne $process -and $process.StartTime.ToUniversalTime().Ticks -eq $script:currentCase.Owned[$_]
        })
        $alive.Count -eq 0
    }
    if ($ExpectLeftover) {
        $leftover = Join-Path $script:currentCase.Session 'keep.lxr'
        Assert-Equal 'keep' ([IO.File]::ReadAllText($leftover)) 'Unrelated file preserved'
        $remaining = @(Get-ChildItem -LiteralPath $script:currentCase.Session)
        Assert-Equal 1 $remaining.Count 'Only unrelated file remains'
        if (-not $script:currentCase.Error.Result.Contains('Cannot remove terminal files from')) {
            throw 'Cleanup leftovers were not reported on launcher stderr.'
        }
    } else {
        if ($null -ne $script:currentCase.Session -and (Test-Path -LiteralPath $script:currentCase.Session)) {
            throw "Session files remain: $($script:currentCase.Session)"
        }
        if ($script:currentCase.Error.Result -ne '') { throw "Launcher diagnostics: $($script:currentCase.Error.Result)" }
    }
    Write-Host "PASS $($script:currentCase.Name) (exit $status)"
    $script:passed++
}

function Stop-Case {
    if ($null -eq $script:currentCase) { return }
    Capture-Descendants
    foreach ($id in @($script:currentCase.Owned.Keys)) {
        $process = Get-Process -Id $id -ErrorAction SilentlyContinue
        if ($null -ne $process -and $process.StartTime.ToUniversalTime().Ticks -eq $script:currentCase.Owned[$id]) {
            try {
                $process.Kill()
                $null = $process.WaitForExit(5000)
            } catch {
                if (-not $process.HasExited) { Write-Warning "Cannot stop owned process $id`: $_" }
            }
        }
    }
    if ($null -ne $script:currentCase.Launcher) { $script:currentCase.Launcher.Dispose() }
    $script:currentCase = $null
}

function Run-CompletionCase([string]$Name, [int]$Status, [bool]$CloseWindow, [string]$WorkingDirectory = $runRoot, [string]$CaseJar = $Jar) {
    $file = Join-Path $WorkingDirectory "$Name.lxr"
    $expected = '42'
    if ($Status -eq 0) { Write-Script $file 'PRINT: 42' }
    elseif ($Status -eq 1) { Write-Script $file 'PRINT: 1 / 0'; $expected = 'RuntimeError at line 3' }
    else { $expected = 'Cannot read script' }
    try {
        # Native Java argv may use an ANSI code page. Resolve the ASCII script
        # filename from the Unicode working directory through Java's Path API.
        Start-Case $Name @([IO.Path]::GetFileName($file)) $WorkingDirectory $CaseJar
        Find-Session
        if ($null -eq $script:canCloseWindow) {
            $script:canCloseWindow = (Invoke-Probe 'window-kind' $script:currentCase.SessionPid) -eq 'win32'
            Write-Host ('Owned console host: ' + (Invoke-Probe 'window-description' $script:currentCase.SessionPid))
        }
        Wait-Completion $Status $expected
        if ($CloseWindow) { $null = Invoke-Probe 'close' $script:currentCase.SessionPid }
        else { $null = Invoke-Probe 'write' $script:currentCase.SessionPid "`r" }
        Assert-ExitAndCleanup $Status
    } finally { Stop-Case }
}

function Skip-WindowCloseCase([string]$Name) {
    Write-Host "SKIP $Name`: Windows Terminal/pseudoconsole has no owned visible Win32 console window."
    $script:skipped++
}

try {
    $script:helper = Join-Path $runRoot 'console-probe.exe'
    Add-Type -TypeDefinition $helperSource -OutputAssembly $script:helper -OutputType ConsoleApplication

    try {
        Start-Case 'picker-cancel' @() $runRoot
        $pickerOwner = Wait-For 'owned file picker' {
            foreach ($id in @($script:currentCase.Owned.Keys)) {
                if ((Invoke-Probe 'picker-find' $id) -eq 'found') { return $id }
            }
        }
        $null = Invoke-Probe 'picker-close' $pickerOwner
        Assert-ExitAndCleanup 0
    } finally { Stop-Case }

    foreach ($status in 0, 1, 2) {
        Run-CompletionCase "status-$status-enter" $status $false
        if ($script:canCloseWindow) { Run-CompletionCase "status-$status-close" $status $true }
        else { Skip-WindowCloseCase "status-$status-close" }
    }

    $cleanup = Join-Path $runRoot 'cleanup.lxr'
    Write-Script $cleanup 'PRINT: 42'
    try {
        Start-Case 'cleanup-leftover-preserves-status' @($cleanup) $runRoot
        Find-Session
        Wait-Completion 0 '42'
        [IO.File]::WriteAllText((Join-Path $script:currentCase.Session 'keep.lxr'), 'keep', $utf8)
        $null = Invoke-Probe 'write' $script:currentCase.SessionPid "`r"
        Assert-ExitAndCleanup 0 $true
    } finally { Stop-Case }

    $scan = Join-Path $runRoot 'scan.lxr'
    Write-Script $scan @'
DECLARE INT x
DECLARE CHAR space, letter
PRINT: "int>"
SCAN: x
PRINT: "space>"
SCAN: space
PRINT: "letter>"
SCAN: letter
PRINT: x & "|" & space & "|" & letter
'@
    try {
        Start-Case 'repeated-scan-space-unicode' @($scan) $runRoot
        Find-Session
        $letter = [char]0x4e2d
        # Wait for the script's prompts before typing, as an interactive user does.
        foreach ($entry in @(@{ Prompt = 'int>'; Input = "7`r" },
                @{ Prompt = 'space>'; Input = " `r" }, @{ Prompt = 'letter>'; Input = "$letter`r" })) {
            $null = Wait-For "SCAN prompt $($entry.Prompt)" {
                (Invoke-Probe 'read' $script:currentCase.SessionPid).Contains($entry.Prompt)
            }
            $null = Invoke-Probe 'write' $script:currentCase.SessionPid $entry.Input
        }
        Wait-Completion 0 "7| |$letter"
        $null = Invoke-Probe 'write' $script:currentCase.SessionPid "`r"
        Assert-ExitAndCleanup 0
    } finally { Stop-Case }

    $special = Join-Path $runRoot ('paths ' + [char]0x4e2d + [char]0xe9 + ' %PATH% ! & # ^')
    $null = New-Item -ItemType Directory -Path $special
    $specialJar = Join-Path $special 'application %PATH% ! &.jar'
    Copy-Item -LiteralPath $Jar -Destination $specialJar
    Run-CompletionCase 'unicode-metacharacter-paths' 0 $false $special ([IO.Path]::GetFileName($specialJar))

    if ($script:canCloseWindow) {
        $loop = Join-Path $runRoot 'loop.lxr'
        Write-Script $loop "PRINT: `"running`" & `$`nREPEAT WHEN (`"TRUE`")`nSTART REPEAT`nEND REPEAT"
        try {
            Start-Case 'infinite-loop-close' @($loop) $runRoot
            Find-Session
            $null = Wait-For 'running script output' { (Invoke-Probe 'read' $script:currentCase.SessionPid).Contains('running') }
            if (Test-Path -LiteralPath (Join-Path $script:currentCase.Session 'session.exit')) { throw 'Infinite loop unexpectedly completed.' }
            $null = Invoke-Probe 'close' $script:currentCase.SessionPid
            Assert-ExitAndCleanup $null
        } finally { Stop-Case }
    } else { Skip-WindowCloseCase 'infinite-loop-close' }

    if ($UncDirectory) {
        $uncParent = (Resolve-Path -LiteralPath $UncDirectory).Path
        if (-not $uncParent.StartsWith('\\') -or -not (Test-Path -LiteralPath $uncParent -PathType Container)) {
            throw '-UncDirectory must be an existing UNC directory.'
        }
        $uncRoot = [IO.Path]::GetFullPath((Join-Path $uncParent $runName))
        $null = New-Item -ItemType Directory -Path $uncRoot
        $ownedRoots += @{ Path = $uncRoot; Parent = $uncParent; Name = $runName }
        $uncJar = Join-Path $uncRoot 'application.jar'
        Copy-Item -LiteralPath $Jar -Destination $uncJar
        Run-CompletionCase 'unc-working-directory' 0 $false $uncRoot ([IO.Path]::GetFileName($uncJar))
    } else { Write-Host 'SKIP UNC working directory: no -UncDirectory supplied.'; $script:skipped++ }
    Write-Host "Native Windows smoke results: $($script:passed) passed, $($script:skipped) skipped."
    if (-not $script:canCloseWindow) {
        Write-Host 'Full window-close coverage requires a visible Win32 console host; host settings were not changed.'
    }
} finally {
    Stop-Case
    foreach ($owned in $ownedRoots) {
        # The generated child must still be exactly beneath its recorded parent.
        $expected = [IO.Path]::GetFullPath((Join-Path $owned.Parent $owned.Name))
        $actual = [IO.Path]::GetFullPath($owned.Path)
        if ($actual -ne $expected -or $owned.Name -ne $runName) { throw 'Refusing cleanup outside the owned test directory.' }
        if (Test-Path -LiteralPath $actual) { Remove-Item -LiteralPath $actual -Recurse -Force }
    }
}
if (-not $script:canCloseWindow) { exit 2 }
