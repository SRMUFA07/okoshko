const readOkoshkoSettings = () => {
  try {
    return JSON.parse(localStorage.getItem("okoshko-settings") || "{}") ?? {};
  } catch {
    return {};
  }
};

const systemDarkTheme = window.matchMedia("(prefers-color-scheme: dark)");
const applyOkoshkoTheme = (theme) => {
  const dark = theme === "dark" || (theme === "system" && systemDarkTheme.matches);
  document.documentElement.classList.toggle("theme-dark", dark);
};

applyOkoshkoTheme(readOkoshkoSettings().theme);
systemDarkTheme.addEventListener("change", () => applyOkoshkoTheme(readOkoshkoSettings().theme));
