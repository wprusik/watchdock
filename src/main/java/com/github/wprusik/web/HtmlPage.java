package com.github.wprusik.web;

public final class HtmlPage {

    public static String content() {
        return """
                <!doctype html>
                <html lang="pl">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>WatchDock</title>
                  <style>
                    :root { color-scheme: light; --bg:#f7f8fb; --ink:#18202f; --muted:#667085; --line:#d9dee8; --ok:#147a42; --bad:#b42318; --warn:#b54708; --panel:#ffffff; }
                    * { box-sizing: border-box; }
                    body { margin:0; font-family: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; background:var(--bg); color:var(--ink); }
                    header { display:flex; align-items:center; justify-content:space-between; gap:16px; padding:18px 28px; border-bottom:1px solid var(--line); background:#fff; position:sticky; top:0; z-index:2; }
                    h1 { margin:0; font-size:22px; letter-spacing:0; }
                    main { max-width:1180px; margin:0 auto; padding:24px; }
                    .summary { display:grid; grid-template-columns:repeat(4, minmax(0, 1fr)); gap:12px; margin-bottom:20px; }
                    .metric, .table-wrap, .alerts { background:var(--panel); border:1px solid var(--line); border-radius:8px; }
                    .metric { padding:14px 16px; min-height:86px; }
                    .metric span { display:block; color:var(--muted); font-size:13px; }
                    .metric strong { display:block; margin-top:8px; font-size:28px; }
                    .toolbar { display:flex; align-items:center; justify-content:space-between; margin:18px 0 10px; gap:12px; }
                    .toolbar h2 { margin:0; font-size:17px; }
                    .pill { display:inline-flex; align-items:center; gap:8px; border:1px solid var(--line); border-radius:999px; padding:7px 11px; color:var(--muted); background:#fff; font-size:13px; white-space:nowrap; }
                    table { width:100%; border-collapse:collapse; table-layout:fixed; }
                    th, td { padding:12px 14px; text-align:left; border-bottom:1px solid var(--line); font-size:14px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; }
                    th { color:var(--muted); font-weight:600; background:#fbfcfe; }
                    tr:last-child td { border-bottom:0; }
                    .state { display:inline-flex; min-width:84px; justify-content:center; border-radius:999px; padding:4px 9px; font-size:12px; font-weight:700; }
                    .running { color:var(--ok); background:#e7f6ed; }
                    .stopped { color:var(--bad); background:#ffebe7; }
                    .restarting { color:var(--warn); background:#fff3dc; }
                    .alerts { padding:6px 0; }
                    .alert { padding:13px 16px; border-bottom:1px solid var(--line); display:grid; gap:4px; }
                    .alert:last-child { border-bottom:0; }
                    .alert strong { font-size:14px; }
                    .alert span { color:var(--muted); font-size:13px; }
                    .empty { padding:22px 16px; color:var(--muted); }
                    @media (max-width: 780px) {
                      header { align-items:flex-start; flex-direction:column; padding:16px; }
                      main { padding:16px; }
                      .summary { grid-template-columns:repeat(2, minmax(0, 1fr)); }
                      th:nth-child(4), td:nth-child(4), th:nth-child(5), td:nth-child(5) { display:none; }
                    }
                  </style>
                </head>
                <body>
                  <header>
                    <h1>WatchDock</h1>
                    <div class="pill" id="refresh">Oczekiwanie na dane</div>
                  </header>
                  <main>
                    <section class="summary">
                      <div class="metric"><span>Kontenery</span><strong id="total">0</strong></div>
                      <div class="metric"><span>Aktywne</span><strong id="running">0</strong></div>
                      <div class="metric"><span>Restartujące</span><strong id="restarting">0</strong></div>
                      <div class="metric"><span>Alerty</span><strong id="alertsCount">0</strong></div>
                    </section>

                    <div class="toolbar">
                      <h2>Kontenery</h2>
                      <div class="pill" id="config">Konfiguracja</div>
                    </div>
                    <div class="table-wrap">
                      <table>
                        <thead><tr><th>Nazwa</th><th>Stan</th><th>Restart count</th><th>ID</th><th>Ostatni odczyt</th></tr></thead>
                        <tbody id="containers"></tbody>
                      </table>
                    </div>

                    <div class="toolbar"><h2>Ostatnie alerty</h2></div>
                    <section class="alerts" id="alerts"></section>
                  </main>
                  <script>
                    const el = id => document.getElementById(id);
                    const fmt = value => value ? new Date(value).toLocaleString() : "-";
                    const safe = value => String(value ?? "").replace(/[&<>"']/g, ch => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[ch]));
                    const stateClass = c => c.restarting ? "restarting" : (c.running ? "running" : "stopped");
                    const stateText = c => c.restarting ? "restarting" : (c.running ? "running" : c.status);

                    async function refresh() {
                      try {
                        const response = await fetch("/api/status", { cache: "no-store" });
                        const data = await response.json();
                        const containers = data.containers || [];
                        const alerts = data.alerts || [];
                        el("total").textContent = containers.length;
                        el("running").textContent = containers.filter(c => c.running).length;
                        el("restarting").textContent = containers.filter(c => c.restarting).length;
                        el("alertsCount").textContent = alerts.length;
                        el("config").textContent = `provider ${data.notificationProvider || "unknown"} | poll ${data.pollIntervalSeconds}s | inactive ${Math.round(data.inactiveThresholdSeconds / 60)}m | restart ${data.restartThreshold}/${data.restartWindowSeconds}s | notify ${data.notificationStartHour}:00-${data.notificationEndHour}:00`;
                        el("containers").innerHTML = containers.length ? containers.map(c => `
                          <tr>
                            <td title="${safe(c.name)}">${safe(c.name)}</td>
                            <td><span class="state ${stateClass(c)}">${safe(stateText(c))}</span></td>
                            <td>${c.restartCount}</td>
                            <td title="${safe(c.id)}">${safe(c.shortId)}</td>
                            <td>${fmt(c.observedAt)}</td>
                          </tr>`).join("") : `<tr><td colspan="5" class="empty">Brak kontenerów albo Docker nie zwrócił jeszcze danych.</td></tr>`;
                        el("alerts").innerHTML = alerts.length ? alerts.map(a => `
                          <div class="alert">
                            <strong>${safe(a.type)} · ${safe(a.containerName || "WatchDock")}</strong>
                            <span>${safe(a.message)}</span>
                            <span>${fmt(a.createdAt)}</span>
                          </div>`).join("") : `<div class="empty">Brak alertów.</div>`;
                        el("refresh").textContent = `Odświeżono ${new Date().toLocaleTimeString()}`;
                      } catch (e) {
                        el("refresh").textContent = "Błąd pobierania danych";
                      }
                    }
                    refresh();
                    setInterval(refresh, 5000);
                  </script>
                </body>
                </html>
                """;
    }
}
