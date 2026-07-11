import { spawn, type ChildProcess, type SpawnOptions } from "node:child_process";

type SpawnHiddenOptions = Omit<SpawnOptions, "shell" | "windowsHide">;

/**
 * Spawn PowerShell on Windows without flashing a console window on the desktop.
 */
export function spawnHiddenPowershellFile(
  scriptPath: string,
  scriptArgs: string[] = [],
  options: SpawnHiddenOptions = {},
): ChildProcess {
  return spawn(
    "powershell.exe",
    [
      "-NoProfile",
      "-WindowStyle",
      "Hidden",
      "-ExecutionPolicy",
      "Bypass",
      "-File",
      scriptPath,
      ...scriptArgs,
    ],
    {
      ...options,
      windowsHide: true,
      stdio: options.stdio ?? ["ignore", "pipe", "pipe"],
    },
  );
}
