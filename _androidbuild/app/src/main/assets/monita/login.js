
(function () {
  "use strict";

  var root = document.getElementById("login-app");
  var pending = new Map();
  var seq = 0;
  var state = {
    boot: null,
    phase: "url",
    info: null,
    busy: false,
    message: null,
    tone: "normal",
    advanced: false
  };

  function esc(v) {
    return String(v == null ? "" : v)
      .replaceAll("&","&amp;").replaceAll("<","&lt;").replaceAll(">","&gt;")
      .replaceAll('"',"&quot;").replaceAll("'","&#039;");
  }

  function request(action, payload) {
    return new Promise(function (resolve, reject) {
      var id = "l" + (++seq);
      pending.set(id, {resolve:resolve, reject:reject});
      MonitaLoginNative.request(action, JSON.stringify(payload || {}), id);
    });
  }

  window.MonitaLogin = {
    nativeResult: function (id, envelope) {
      var p = pending.get(id);
      if (!p) return;
      pending.delete(id);
      envelope && envelope.ok ? p.resolve(envelope.data) : p.reject((envelope && envelope.error) || {message:"Request failed"});
    },
    nativeEvent: function (name, payload) {
      if (name === "state") {
        state.phase = payload.state || state.phase;
        if (payload.info) state.info = payload.info;
        render();
      } else if (name === "error") {
        showMessage(payload.message || "Request failed", "error");
      } else if (name === "connectionSettings") {
        state.boot = Object.assign({}, state.boot || {}, payload || {});
        render();
      }
    }
  };

  function showMessage(message, tone) {
    state.message = message;
    state.tone = tone || "normal";
    render();
  }

  function spinner() {
    return '<span class="inline-block h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white"></span>';
  }

  function render() {
    var b = state.boot || {};
    var ready = state.phase === "ready" || state.phase === "loggingIn" || state.phase === "oidc";
    var busy = state.phase === "checking" || state.phase === "loggingIn" || state.phase === "oidc";
    var oidc = !!(state.info && state.info.oidc);

    root.innerHTML =
      '<main class="safe-bottom flex min-h-screen items-center justify-center px-5 py-8">' +
        '<section class="w-full max-w-md">' +
          '<div class="mb-7 flex items-center gap-4">' +
            '<img src="monita-icon.png" alt="Monita" class="h-16 w-16 rounded-xl object-contain shadow-sm">' +
            '<div><div class="text-xs font-extrabold uppercase tracking-[0.18em] text-monita-600 dark:text-monita-400">Monita</div>' +
            '<h1 class="mt-1 text-3xl font-black tracking-tight">Connect your device</h1></div>' +
          '</div>' +

          '<div class="overflow-hidden rounded-xl border border-slate-200 bg-white shadow-sm shadow-slate-200/20 dark:border-slate-800 dark:bg-[#151a20] dark:shadow-black/20">' +
            '<div class="space-y-4 p-5">' +
              '<div><label class="mb-1.5 block text-xs font-extrabold uppercase tracking-wide text-slate-500">Server address</label>' +
              '<input id="server-url" type="url" inputmode="url" autocomplete="url" value="' + esc(b.serverUrl || "") + '" placeholder="https://push.example.com" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-[15px] outline-none ring-monita-500 focus:ring-2 dark:border-slate-700 dark:bg-slate-900"></div>' +

              '<button id="check-server" class="flex w-full items-center justify-center gap-2 rounded-xl bg-monita-600 px-4 py-3.5 text-sm font-extrabold text-white shadow-sm active:bg-monita-700" ' + (busy ? "disabled" : "") + '>' +
                (state.phase === "checking" ? spinner() + " Checking server" : (ready ? "Recheck server" : "Continue")) +
              '</button>' +

              (ready ? loginFields(b, oidc, busy) : '') +

              '<button id="advanced-toggle" class="w-full rounded-xl px-3 py-2 text-center text-xs font-bold text-slate-500 hover:bg-slate-50 dark:text-slate-400 dark:hover:bg-slate-900">' +
                (state.advanced ? "Hide advanced connection settings" : "Advanced connection settings") +
              '</button>' +
              (state.advanced ? advancedFields(b) : '') +
            '</div>' +

            '<div class="border-t border-slate-100 bg-slate-50/80 px-5 py-4 text-xs leading-5 text-slate-500 dark:border-slate-800 dark:bg-slate-900/50 dark:text-slate-400">' +
              'Your client token stays in Android secure app storage and is never exposed to the Tailwind interface.' +
            '</div>' +
          '</div>' +

          (state.message ? '<div class="mt-4 rounded-xl border px-4 py-3 text-sm font-semibold ' +
            (state.tone === "error" ? "border-red-200 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200" : "border-slate-200 bg-white text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200") +
            '">' + esc(state.message) + '</div>' : '') +

          '<button id="open-logs" class="mt-5 w-full text-center text-xs font-bold text-slate-400">Connection logs</button>' +
        '</section>' +
      '</main>';

    bind();
  }

  function loginFields(b, oidc, busy) {
    return '<div class="border-t border-slate-100 pt-4 dark:border-slate-800">' +
      '<div class="mb-4 flex items-center justify-between gap-3"><div><div class="text-sm font-extrabold">Server ready</div>' +
      '<div class="text-xs text-slate-500 dark:text-slate-400">' + esc((state.info && state.info.version) || "") + '</div></div>' +
      '<span class="rounded-lg bg-monita-100 px-2 py-1 text-[11px] font-extrabold uppercase tracking-wide text-monita-800 dark:bg-monita-950 dark:text-monita-300">Connected</span></div>' +
      '<div class="space-y-3">' +
        '<input id="username" autocomplete="username" placeholder="Username" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-[15px] outline-none ring-monita-500 focus:ring-2 dark:border-slate-700 dark:bg-slate-900">' +
        '<input id="password" type="password" autocomplete="current-password" placeholder="Password" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-[15px] outline-none ring-monita-500 focus:ring-2 dark:border-slate-700 dark:bg-slate-900">' +
        '<input id="client-name" value="' + esc(b.defaultClientName || "Android") + '" placeholder="Device name" class="w-full rounded-xl border border-slate-200 bg-slate-50 px-4 py-3.5 text-[15px] outline-none ring-monita-500 focus:ring-2 dark:border-slate-700 dark:bg-slate-900">' +
        '<button id="sign-in" class="flex w-full items-center justify-center gap-2 rounded-xl bg-monita-600 px-4 py-3.5 text-sm font-extrabold text-white" ' + (busy ? "disabled" : "") + '>' +
          (state.phase === "loggingIn" ? spinner() + " Signing in" : "Sign in") +
        '</button>' +
        (oidc ? '<button id="oidc-sign-in" class="w-full rounded-xl border border-slate-200 px-4 py-3.5 text-sm font-extrabold dark:border-slate-700">Sign in with ' + esc((state.info && state.info.oidcIdpName) || "SSO") + '</button>' : '') +
      '</div></div>';
  }

  function advancedFields(b) {
    return '<div class="space-y-3 rounded-xl bg-slate-50 p-4 dark:bg-slate-900">' +
      '<label class="flex items-center justify-between gap-4"><span><span class="block text-sm font-bold">Validate TLS certificates</span><span class="block text-xs text-slate-500 dark:text-slate-400">Recommended for normal HTTPS servers.</span></span>' +
      '<input id="validate-ssl" type="checkbox" class="h-5 w-5 accent-blue-700" ' + (b.validateSsl !== false ? "checked" : "") + '></label>' +
      '<div class="grid grid-cols-2 gap-2">' +
        '<button id="select-ca" class="rounded-xl border border-slate-200 px-3 py-2.5 text-xs font-extrabold dark:border-slate-700">' + (b.hasCa ? "Replace CA cert" : "Custom CA cert") + '</button>' +
        '<button id="select-client-cert" class="rounded-xl border border-slate-200 px-3 py-2.5 text-xs font-extrabold dark:border-slate-700">' + (b.hasClientCert ? "Replace client cert" : "Client certificate") + '</button>' +
      '</div>' +
      (b.hasCa ? '<button id="remove-ca" class="text-xs font-bold text-red-600 dark:text-red-400">Remove custom CA certificate</button>' : '') +
      (b.hasClientCert ? '<button id="remove-client-cert" class="block text-xs font-bold text-red-600 dark:text-red-400">Remove client certificate</button>' : '') +
      '<input id="client-cert-password" type="password" placeholder="' + (b.hasClientCertPassword ? "Client certificate password is set" : "Client certificate password (if required)") + '" class="w-full rounded-xl border border-slate-200 bg-white px-3 py-3 text-sm outline-none dark:border-slate-700 dark:bg-[#151a20]">' +
      '</div>';
  }

  function bind() {
    var check = document.getElementById("check-server");
    if (check) check.addEventListener("click", async function () {
      var url = document.getElementById("server-url").value.trim().replace(/\/+$/, "");
      if (!/^https?:\/\//i.test(url)) {
        showMessage("Include http:// or https:// in the server address.", "error");
        return;
      }
      if (/^http:\/\//i.test(url) && !confirm("This server uses unencrypted HTTP. Continue anyway?")) return;
      state.message = null;
      state.phase = "checking";
      render();
      try {
        var info = await request("checkUrl", {url:url});
        state.info = info;
        state.phase = "ready";
        if (state.boot) state.boot.serverUrl = url;
        render();
      } catch (e) {
        state.phase = "url";
        showMessage((e && e.message) || "Could not reach server.", "error");
      }
    });

    var login = document.getElementById("sign-in");
    if (login) login.addEventListener("click", async function () {
      var username = document.getElementById("username").value;
      var password = document.getElementById("password").value;
      var clientName = document.getElementById("client-name").value.trim() || state.boot.defaultClientName;
      if (!username || !password) {
        showMessage("Enter your username and password.", "error");
        return;
      }
      state.phase = "loggingIn";
      state.message = null;
      render();
      try {
        await request("login", {username:username, password:password, clientName:clientName});
      } catch (e) {
        state.phase = "ready";
        showMessage((e && e.message) || "Sign in failed.", "error");
      }
    });

    var oidc = document.getElementById("oidc-sign-in");
    if (oidc) oidc.addEventListener("click", async function () {
      var clientName = document.getElementById("client-name").value.trim() || state.boot.defaultClientName;
      state.phase = "oidc";
      render();
      try { await request("oidc", {clientName:clientName}); }
      catch (e) { state.phase = "ready"; showMessage((e && e.message) || "SSO sign in failed.", "error"); }
    });

    var adv = document.getElementById("advanced-toggle");
    if (adv) adv.addEventListener("click", function () { state.advanced = !state.advanced; render(); });

    var validate = document.getElementById("validate-ssl");
    if (validate) validate.addEventListener("change", async function () {
      await request("setValidateSsl", {enabled:validate.checked});
      state.boot.validateSsl = validate.checked;
    });

    var selectCa = document.getElementById("select-ca");
    if (selectCa) selectCa.addEventListener("click", function () { request("selectCa"); });
    var selectClient = document.getElementById("select-client-cert");
    if (selectClient) selectClient.addEventListener("click", function () { request("selectClientCert"); });
    var removeCa = document.getElementById("remove-ca");
    if (removeCa) removeCa.addEventListener("click", async function () {
      await request("removeCa"); state.boot.hasCa = false; render();
    });
    var removeClient = document.getElementById("remove-client-cert");
    if (removeClient) removeClient.addEventListener("click", async function () {
      await request("removeClientCert"); state.boot.hasClientCert = false; render();
    });
    var certPass = document.getElementById("client-cert-password");
    if (certPass) certPass.addEventListener("change", async function () {
      await request("setClientCertPassword", {password:certPass.value});
      state.boot.hasClientCertPassword = certPass.value.length > 0;
      certPass.value = "";
    });
    var logs = document.getElementById("open-logs");
    if (logs) logs.addEventListener("click", function () { request("openLogs"); });
  }

  request("bootstrap").then(function (boot) {
    state.boot = boot;
    if (boot.serverUrl) document.title = "Sign in · Monita";
    render();
  }).catch(function (e) {
    showMessage((e && e.message) || "Could not initialize login.", "error");
  });

  render();
})();
