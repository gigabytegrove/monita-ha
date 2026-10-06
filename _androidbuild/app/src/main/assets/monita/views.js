(function () {
  "use strict";

  function esc(value) {
    return String(value == null ? "" : value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function fmtDate(value) {
    if (!value) return "";
    var d = new Date(value);
    if (Number.isNaN(d.getTime())) return "";
    var diff = Date.now() - d.getTime();
    if (diff < 60000) return "now";
    if (diff < 3600000) return Math.floor(diff / 60000) + "m";
    if (diff < 86400000) return Math.floor(diff / 3600000) + "h";
    return d.toLocaleDateString(undefined, {month: "short", day: "numeric"});
  }

  function icon(name, cls) {
    cls = cls || "h-5 w-5";
    var paths = {
      inbox: '<path d="M4 4h16v12H4z"/><path d="M4 12h4l2 3h4l2-3h4"/>',
      channels: '<path d="M4 5h16v10H8l-4 4z"/>',
      archive: '<path d="M4 7h16v13H4z"/><path d="M3 3h18v4H3z"/><path d="M9 11h6"/>',
      admin: '<path d="M12 3l8 3v5c0 5-3.5 8.5-8 10-4.5-1.5-8-5-8-10V6z"/><path d="M9.5 12l1.7 1.7 3.5-4"/>',
      chat: '<path d="M4 5h16v11H8l-4 4z"/><path d="M8 9h8M8 12h5"/>',
      bell: '<path d="M18 8a6 6 0 10-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9"/><path d="M10 21h4"/>',
      belloff: '<path d="M13.7 21H10"/><path d="M18.6 13.6A18 18 0 0118 8a6 6 0 00-9.3-5"/><path d="M6.3 6.3A6 6 0 006 8c0 7-3 7-3 9h14"/><path d="M3 3l18 18"/>',
      send: '<path d="M22 2L11 13"/><path d="M22 2l-7 20-4-9-9-4z"/>',
      paperclip: '<path d="M21.4 11.6l-8.9 8.9a6 6 0 01-8.5-8.5l9.6-9.6a4 4 0 015.7 5.7l-9.6 9.6a2 2 0 01-2.8-2.8l8.9-8.9"/>',
      chevron: '<path d="M9 18l6-6-6-6"/>',
      back: '<path d="M19 12H5"/><path d="M12 19l-7-7 7-7"/>',
      refresh: '<path d="M20 11a8 8 0 10-2.3 5.7"/><path d="M20 4v7h-7"/>',
      sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M4.9 4.9l1.4 1.4m11.4 11.4l1.4 1.4M2 12h2m16 0h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
      logout: '<path d="M10 17l5-5-5-5"/><path d="M15 12H3"/><path d="M21 19V5a2 2 0 00-2-2h-6"/>',
      lock: '<rect x="4" y="10" width="16" height="11" rx="2"/><path d="M8 10V7a4 4 0 018 0v3"/>',
      plus: '<path d="M12 5v14M5 12h14"/>',
      users: '<path d="M16 21v-2a4 4 0 00-4-4H6a4 4 0 00-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 00-3-3.87M16 3.13a4 4 0 010 7.75"/>',
      group: '<circle cx="8" cy="8" r="3"/><circle cx="17" cy="8" r="3"/><path d="M2 21v-2a5 5 0 015-5h2a5 5 0 015 5v2"/><path d="M14 15a5 5 0 018 4v2"/>',
      edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 013 3L8 18l-4 1 1-4z"/>',
      trash: '<path d="M3 6h18"/><path d="M8 6V4h8v2M19 6l-1 15H6L5 6"/><path d="M10 11v6M14 11v6"/>',
      audit: '<path d="M6 3h12v18H6z"/><path d="M9 7h6M9 11h6M9 15h4"/>',
      settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 00.3 1.9l.1.1-2.8 2.8-.1-.1a1.7 1.7 0 00-1.9-.3 1.7 1.7 0 00-1 1.6V21h-4v-.1a1.7 1.7 0 00-1-1.6 1.7 1.7 0 00-1.9.3l-.1.1L4.2 17l.1-.1a1.7 1.7 0 00.3-1.9 1.7 1.7 0 00-1.6-1H3v-4h.1a1.7 1.7 0 001.6-1 1.7 1.7 0 00-.3-1.9L4.2 7 7 4.2l.1.1a1.7 1.7 0 001.9.3 1.7 1.7 0 001-1.6V3h4v.1a1.7 1.7 0 001 1.6 1.7 1.7 0 001.9-.3l.1-.1L19.8 7l-.1.1a1.7 1.7 0 00-.3 1.9 1.7 1.7 0 001.6 1h.1v4H21a1.7 1.7 0 00-1.6 1z"/>'
    };
    return '<svg class="' + cls + '" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (paths[name] || "") + '</svg>';
  }

  function serverLabel(url) {
    try { return new URL(url).host || url; } catch (_) { return url || ""; }
  }

  function isChat(channel) {
    return !!channel && (channel.channelType === "chat" || (channel.channelType == null && channel.allowMemberPost === true));
  }

  function canPost(channel) {
    var role = String(channel && channel.role || "").toLowerCase();
    if (role === "owner" || role === "manager" || role === "publisher") return true;
    if (role === "readonly") return false;
    return !!(channel && channel.allowMemberPost);
  }

  function badge(text, tone) {
    tone = tone || "neutral";
    var tones = {
      neutral: "bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300",
      green: "bg-monita-50 text-monita-800 dark:bg-monita-950 dark:text-monita-300",
      blue: "bg-monita-50 text-monita-800 dark:bg-monita-950 dark:text-monita-300",
      amber: "bg-amber-50 text-amber-800 dark:bg-amber-950/60 dark:text-amber-300",
      red: "bg-red-50 text-red-700 dark:bg-red-950/60 dark:text-red-300"
    };
    return '<span class="inline-flex items-center rounded-md px-2 py-0.5 text-[10px] font-extrabold uppercase tracking-[0.08em] ' + tones[tone] + '">' + esc(text) + '</span>';
  }

  function statCard(value, label) {
    return '<div class="rounded-lg border border-slate-200 bg-white px-3 py-2.5 dark:border-slate-800 dark:bg-[#151a20]">' +
      '<div class="text-lg font-black tracking-tight">' + esc(value) + '</div>' +
      '<div class="mt-0.5 text-[10px] font-bold uppercase tracking-[0.08em] text-slate-400">' + esc(label) + '</div></div>';
  }

  function emptyState(title, body) {
    return '<div class="rounded-xl border border-dashed border-slate-300 px-5 py-7 text-center dark:border-slate-700">' +
      '<div class="text-sm font-extrabold">' + esc(title) + '</div>' +
      (body ? '<div class="mx-auto mt-1.5 max-w-sm text-sm leading-5 text-slate-500 dark:text-slate-400">' + esc(body) + '</div>' : '') +
      '</div>';
  }

  function channelById(state, id) {
    return (state.boot.channels || []).find(function (c) { return Number(c.id) === Number(id); });
  }

  function isProtectedChannel(state, id) {
    var ids = state.boot && state.boot.security && state.boot.security.protectedChannelIds || [];
    return ids.some(function (value) { return Number(value) === Number(id); });
  }

  function safeImageUrl(value) {
    if (typeof value !== "string" || !value.trim()) return null;
    try {
      var parsed = new URL(value.trim());
      if (parsed.protocol !== "http:" && parsed.protocol !== "https:") return null;
      return parsed.href;
    } catch (_) {
      return null;
    }
  }

  function channelImageUrl(state, channel) {
    if (!channel || !channel.image || channel.image === "static/defaultapp.png") return null;
    var raw = String(channel.image).trim();
    if (!raw) return null;
    if (/^https?:\/\//i.test(raw)) return safeImageUrl(raw);

    var base = state && state.boot && state.boot.serverUrl;
    if (!base) return null;
    try {
      var normalizedBase = /\/$/.test(base) ? base : base + "/";
      return safeImageUrl(new URL(raw.replace(/^\/+/, ""), normalizedBase).href);
    } catch (_) {
      return null;
    }
  }

  function channelVisual(state, channel, classes) {
    var image = channelImageUrl(state, channel);
    var fallback = icon(isChat(channel) ? "chat" : "bell", "h-5 w-5");
    return '<div class="relative flex ' + classes + ' shrink-0 items-center justify-center overflow-hidden rounded-lg bg-monita-50 text-monita-700 dark:bg-monita-950 dark:text-monita-300">' +
      fallback +
      (image ? '<img data-channel-image src="' + esc(image) + '" alt="" class="absolute inset-0 h-full w-full object-cover">' : '') +
      '</div>';
  }

  function imageItems(message) {
    if (!message || message.protected) return [];

    var extras = message.extras && typeof message.extras === "object" ? message.extras : {};
    var display = extras["monita::display"];
    var raw = display && Array.isArray(display.images) ? display.images : [];
    var images = [];
    var seen = {};

    raw.slice(0, 8).forEach(function (item) {
      if (!item || typeof item !== "object") return;
      var url = safeImageUrl(item.url);
      if (!url || seen[url]) return;
      seen[url] = true;
      images.push({
        url: url,
        filename: String(item.filename || ""),
        contentType: String(item.contentType || ""),
        size: Number(item.size || 0)
      });
    });

    if (!images.length) {
      var notification = extras["client::notification"];
      var fallback = notification && safeImageUrl(notification.bigImageUrl);
      if (fallback) {
        images.push({
          url: fallback,
          filename: "",
          contentType: "",
          size: 0
        });
      }
    }

    return images;
  }

  function renderMessageImages(message, dense) {
    var images = imageItems(message);
    if (!images.length) return "";

    var columns = images.length === 1 ? "grid-cols-1" : "grid-cols-2";
    var maxHeight = dense ? "max-h-56" : "max-h-80";
    return '<div class="mt-3 grid ' + columns + ' gap-2 overflow-hidden rounded-xl">' +
      images.map(function (image, index) {
        var label = image.filename || ("Image " + (index + 1));
        return '<button type="button" data-image-open data-image-url="' + esc(image.url) + '" data-image-label="' + esc(label) + '" class="group relative overflow-hidden rounded-xl border border-slate-200 bg-slate-100 text-left dark:border-slate-700 dark:bg-slate-900" aria-label="Open ' + esc(label) + '">' +
          '<img data-message-image src="' + esc(image.url) + '" alt="' + esc(label) + '" loading="lazy" decoding="async" referrerpolicy="no-referrer" class="block h-auto w-full ' + maxHeight + ' object-contain">' +
          (image.filename ? '<span class="absolute inset-x-0 bottom-0 truncate bg-black/60 px-2.5 py-1.5 text-[11px] font-semibold text-white opacity-0 transition-opacity group-focus:opacity-100 group-active:opacity-100">' + esc(image.filename) + '</span>' : '') +
          '</button>';
      }).join("") + '</div>';
  }

  function attachmentImages(message) {
    if (!message || message.protected) return [];
    var collaboration = message.collaboration && typeof message.collaboration === "object" ? message.collaboration : {};
    var attachments = Array.isArray(collaboration.attachments) ? collaboration.attachments : [];
    return attachments.filter(function (item) {
      return item && /^image\/(jpeg|png|gif|webp)$/i.test(String(item.contentType || ""));
    }).slice(0, 8);
  }

  function renderMentionText(message, mine) {
    var text = esc(String((message && message.message) || ""));
    var mentionClass = mine
      ? "rounded bg-white/20 px-0.5 font-extrabold text-white"
      : "rounded bg-monita-50 px-0.5 font-extrabold text-monita-700 dark:bg-monita-950 dark:text-monita-300";
    return text
      .replace(/(^|[\\s([{,;:!?])(@[A-Za-z0-9._-]+)/g, function (_match, prefix, mention) {
        return prefix + '<span class="' + mentionClass + '">' + mention + '</span>';
      })
      .replaceAll("\n", "<br>");
  }

  function mentionedYouBadge(message, mine) {
    var collaboration = message && message.collaboration && typeof message.collaboration === "object"
      ? message.collaboration
      : {};
    if (!collaboration.mentioned) return "";
    return '<span class="inline-flex items-center rounded-full px-2 py-0.5 text-[10px] font-extrabold ' +
      (mine
        ? 'bg-white/20 text-white'
        : 'bg-monita-50 text-monita-700 dark:bg-monita-950 dark:text-monita-300') +
      '">@ You</span>';
  }

  function configuredMessageControls(message) {
    var extras = message && message.extras && typeof message.extras === "object" ? message.extras : {};
    var raw = extras["monita::controls"];
    return Array.isArray(raw) ? raw.map(function (value) { return String(value).toLowerCase(); }) : [];
  }

  function hasMessageControl(message, control) {
    return configuredMessageControls(message).indexOf(control) >= 0;
  }

  function messageWorkflow(message) {
    return message && message.collaboration && typeof message.collaboration === "object"
      ? message.collaboration
      : {};
  }

  function renderMessageControls(state, message, mine) {
    var collaboration = messageWorkflow(message);
    var controls = [];
    var ownId = state && state.boot && state.boot.user ? Number(state.boot.user.id) : 0;
    var assignedToMe =
      ownId > 0 &&
      Number(collaboration.assignedUserId || 0) === ownId;
    var assignedUserName = collaboration.assignedUserName || "";
    var status = String(collaboration.status || "open").toLowerCase();

    if (hasMessageControl(message, "assign")) {
      controls.push(
        '<button data-assign-message="' + esc(message.id) + '" data-assigned-to-me="' + (assignedToMe ? "true" : "false") + '" class="' +
          (mine
            ? 'rounded-lg bg-white/15 px-2.5 py-1.5 text-[11px] font-extrabold text-white'
            : 'rounded-lg bg-slate-100 px-2.5 py-1.5 text-[11px] font-extrabold text-slate-700 dark:bg-slate-800 dark:text-slate-200') +
          '">' +
          (assignedToMe
            ? 'Assigned to me'
            : assignedUserName
              ? 'Assigned: ' + esc(assignedUserName)
              : 'Assign to me') +
        '</button>'
      );
    }

    if (hasMessageControl(message, "resolve")) {
      controls.push(
        '<button data-resolve-message="' + esc(message.id) + '" data-status="' + esc(status) + '" class="' +
          (mine
            ? 'rounded-lg bg-white/15 px-2.5 py-1.5 text-[11px] font-extrabold text-white'
            : 'rounded-lg bg-slate-100 px-2.5 py-1.5 text-[11px] font-extrabold text-slate-700 dark:bg-slate-800 dark:text-slate-200') +
          '">' + (status === "resolved" ? "Reopen" : "Resolve") + '</button>'
      );
    }

    if (hasMessageControl(message, "attach")) {
      controls.push(
        '<input id="message-attach-' + esc(message.id) + '" data-message-attach-input="' + esc(message.id) + '" type="file" class="hidden">' +
        '<button data-attach-message="' + esc(message.id) + '" class="' +
          (mine
            ? 'rounded-lg bg-white/15 px-2.5 py-1.5 text-[11px] font-extrabold text-white'
            : 'rounded-lg bg-slate-100 px-2.5 py-1.5 text-[11px] font-extrabold text-slate-700 dark:bg-slate-800 dark:text-slate-200') +
          '">Attach</button>'
      );
    }

    if (!controls.length) return "";
    return '<div class="mt-2 flex flex-wrap gap-1.5">' + controls.join("") + '</div>';
  }

  function configuredMessageControls(message) {
    var extras = message && message.extras && typeof message.extras === "object" ? message.extras : {};
    var controls = extras["monita::controls"];
    if (!Array.isArray(controls)) return [];
    return controls.map(function (value) { return String(value || "").toLowerCase(); });
  }

  function renderMessageControls(state, message, mine) {
    var features = state && state.boot && state.boot.capabilities && state.boot.capabilities.features;
    if (!(features && features.messageControls === true)) return "";

    var controls = configuredMessageControls(message);
    if (!controls.length) return "";

    var collaboration = message.collaboration && typeof message.collaboration === "object"
      ? message.collaboration
      : {};
    var currentUser = (state.boot && (state.boot.user || state.boot.settingsUser)) || {};
    var currentUserId = Number(currentUser.id || 0);
    var assignedToMe =
      currentUserId > 0 && Number(collaboration.assignedUserId || 0) === currentUserId;
    var status = String(collaboration.status || "open").toLowerCase();
    var classes = mine
      ? "border-white/30 bg-white/10 text-white"
      : "border-slate-200 bg-slate-50 text-slate-700 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-200";
    var buttons = [];

    if (controls.indexOf("assign") >= 0 && currentUserId > 0) {
      buttons.push(
        '<button type="button" data-message-assign="' + message.id +
        '" data-current-user-id="' + currentUserId +
        '" data-assigned="' + (assignedToMe ? "true" : "false") +
        '" class="rounded-lg border px-2.5 py-1.5 text-[11px] font-extrabold ' + classes + '">' +
        (assignedToMe ? "Assigned to me" : "Assign to Me") + '</button>'
      );
    }
    if (controls.indexOf("resolve") >= 0) {
      buttons.push(
        '<button type="button" data-message-resolve="' + message.id +
        '" data-status="' + esc(status) +
        '" class="rounded-lg border px-2.5 py-1.5 text-[11px] font-extrabold ' + classes + '">' +
        (status === "resolved" ? "Reopen" : "Resolve") + '</button>'
      );
    }
    if (controls.indexOf("attach") >= 0) {
      buttons.push(
        '<button type="button" data-message-attach="' + message.id +
        '" class="rounded-lg border px-2.5 py-1.5 text-[11px] font-extrabold ' + classes + '">' +
        icon("paperclip", "mr-1 inline h-3.5 w-3.5") + "Attach</button>"
      );
    }
    if (!buttons.length) return "";
    return '<div class="mt-3 flex flex-wrap gap-2">' + buttons.join("") + '</div>';
  }

  function renderAttachmentImages(message, dense) {
    var images = attachmentImages(message);
    if (!images.length) return "";
    var columns = images.length === 1 ? "grid-cols-1" : "grid-cols-2";
    var maxHeight = dense ? "max-h-56" : "max-h-80";
    return '<div class="mt-3 grid ' + columns + ' gap-2 overflow-hidden rounded-xl">' +
      images.map(function (image, index) {
        var label = image.filename || ("Photo " + (index + 1));
        return '<button type="button" data-secure-image-open data-app-id="' + esc(message.appid) + '" data-message-id="' + esc(message.id) + '" data-attachment-id="' + esc(image.id) + '" data-image-label="' + esc(label) + '" class="group relative min-h-28 overflow-hidden rounded-xl border border-slate-200 bg-slate-100 text-left dark:border-slate-700 dark:bg-slate-900" aria-label="Open ' + esc(label) + '">' +
          '<img data-secure-message-image data-app-id="' + esc(message.appid) + '" data-message-id="' + esc(message.id) + '" data-attachment-id="' + esc(image.id) + '" alt="' + esc(label) + '" class="hidden h-auto w-full ' + maxHeight + ' object-contain">' +
          '<span data-secure-image-loading class="flex min-h-28 items-center justify-center px-3 text-center text-xs font-bold text-slate-500 dark:text-slate-400">Loading photo…</span>' +
          '</button>';
      }).join("") + '</div>';
  }

  function chatImagesSupported(state) {
    var features = state && state.boot && state.boot.capabilities && state.boot.capabilities.features;
    return !!(features && features.chatImages === true);
  }
  function sectionMode(state, section) {
    return (state.viewModes && state.viewModes[section]) || "active";
  }

  function modeTabs(state, section) {
    var active = sectionMode(state, section);
    return '<div class="inline-flex rounded-lg bg-slate-100 p-1 dark:bg-slate-800">' +
      ['active','archived'].map(function (mode) {
        var selected = active === mode;
        return '<button data-view-section="' + section + '" data-view-mode="' + mode + '" class="rounded-md px-3 py-1.5 text-xs font-extrabold ' +
          (selected ? 'bg-white text-slate-900 shadow-sm dark:bg-slate-700 dark:text-white' : 'text-slate-500 dark:text-slate-400') + '">' +
          (mode === 'active' ? 'Active' : 'Archived') + '</button>';
      }).join('') + '</div>';
  }

  function shell(state, content) {
    var boot = state.boot || {};
    var adminVisible = !!(boot.isMonita && ((boot.user && boot.user.admin) || (boot.settingsUser && boot.settingsUser.admin)));
    var tabs = [["inbox","Inbox","inbox"],["chats","Chats","chat"],["channels","Channels","channels"]];
    if (adminVisible) tabs.push(["admin","Admin","admin"]);
    var cols = tabs.length === 4 ? "grid-cols-4" : "grid-cols-3";
    var nav = tabs.map(function (t) {
      var active = state.tab === t[0] || (state.tab === "channel" && state.channelReturnTab === t[0]);
      return '<button data-tab="' + t[0] + '" class="flex min-h-[56px] flex-col items-center justify-center gap-0.5 px-1 py-1 text-[10px] font-semibold ' +
        (active ? 'text-monita-700 dark:text-monita-300' : 'text-slate-500 dark:text-slate-400') + '">' +
        '<span class="' + (active ? 'bg-monita-50 text-monita-700 dark:bg-monita-950/70 dark:text-monita-300 ' : '') +
        'rounded-lg px-3 py-1">' + icon(t[2], "h-[19px] w-[19px]") + '</span>' + t[1] + '</button>';
    }).join("");

    var toast = "";
    if (state.toast) {
      var tone = state.toast.tone === "error"
        ? "border-red-200 bg-red-50 text-red-800 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
        : "border-slate-200 bg-white text-slate-800 dark:border-slate-700 dark:bg-slate-900 dark:text-slate-100";
      toast = '<div class="fixed inset-x-4 bottom-[68px] z-[60] mx-auto max-w-md rounded-xl border px-4 py-3 text-sm font-semibold shadow-lg ' + tone + '">' + esc(state.toast.message) + '</div>';
    }

    return '<div class="min-h-screen pb-[66px]">' +
      '<header class="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur dark:border-slate-800 dark:bg-[#151a20]/95">' +
      '<div class="mx-auto flex h-14 max-w-3xl items-center gap-2.5 px-4">' +
      '<img src="monita-icon.png" alt="Monita" class="h-8 w-9 shrink-0 object-contain">' +
      '<div class="min-w-0 flex-1"><div class="truncate text-sm font-extrabold tracking-tight">Monita</div>' +
      '<div class="truncate text-[11px] text-slate-500 dark:text-slate-400">' + esc(serverLabel(boot.serverUrl || "")) + '</div></div>' +
      '<button data-action="refresh" class="rounded-lg p-2 text-slate-500 active:bg-slate-100 dark:text-slate-400 dark:active:bg-slate-800" aria-label="Refresh">' + icon("refresh","h-[18px] w-[18px]") + '</button>' +
      '<button data-action="security" class="rounded-lg p-2 text-slate-500 active:bg-slate-100 dark:text-slate-400 dark:active:bg-slate-800" aria-label="Security">' + icon("lock","h-[18px] w-[18px]") + '</button>' +
      '<button data-action="theme" class="rounded-lg p-2 text-slate-500 active:bg-slate-100 dark:text-slate-400 dark:active:bg-slate-800" aria-label="Theme">' + icon("sun","h-[18px] w-[18px]") + '</button>' +
      '<button data-action="logout" class="rounded-lg p-2 text-slate-500 active:bg-red-50 active:text-red-600 dark:text-slate-400 dark:active:bg-red-950/40" aria-label="Log out">' + icon("logout","h-[18px] w-[18px]") + '</button>' +
      '</div></header>' +
      '<main class="mx-auto max-w-3xl px-4 py-4">' + content + '</main>' +
      '<nav class="fixed inset-x-0 bottom-0 z-50 border-t border-slate-200 bg-white/98 backdrop-blur dark:border-slate-800 dark:bg-[#151a20]/98">' +
      '<div class="mx-auto grid max-w-3xl ' + cols + ' px-2 py-1">' + nav + '</div></nav>' + toast + '</div>';
  }

  function loading(state, label) {
    return shell(state, '<div class="flex min-h-[55vh] flex-col items-center justify-center gap-4">' +
      '<div class="h-9 w-9 animate-spin rounded-full border-4 border-slate-200 border-t-mu-500 dark:border-slate-800 dark:border-t-mu-400"></div>' +
      '<div class="text-sm font-semibold text-slate-500 dark:text-slate-400">' + esc(label || "Loading") + '…</div></div>');
  }

  function messageCard(state, message, archived) {
    var channel = channelById(state, message.appid);
    var protectedChannel = isProtectedChannel(state, message.appid);
    var protectedMessage = !!message.protected;
    var currentUser = (state.boot && (state.boot.user || state.boot.settingsUser)) || {};
    var isAdmin = !!currentUser.admin;
    return '<article class="rounded-lg border border-slate-200 bg-white p-4 dark:border-slate-800 dark:bg-[#151a20]">' +
      '<div class="mb-3 flex items-start gap-3">' +
      '<div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-monita-100 font-black text-monita-700 dark:bg-monita-950 dark:text-monita-300">' +
      esc(((channel && channel.name) || "?").slice(0, 2).toUpperCase()) + '</div>' +
      '<div class="min-w-0 flex-1"><div class="flex items-start justify-between gap-3"><div class="min-w-0">' +
      '<div class="flex items-center gap-1.5"><div class="truncate text-sm font-extrabold">' + esc(message.senderName || message.title || (channel && channel.name) || "Notification") + '</div>' + ((protectedMessage || protectedChannel) ? badge("Protected","amber") : "") + '</div>' +
      '<button data-open-channel="' + message.appid + '" data-open-archived="' + (archived ? 'true' : 'false') + '" class="mt-0.5 truncate text-left text-xs font-semibold text-monita-600 dark:text-monita-400">' + esc((channel && channel.name) || "Channel") + '</button>' +
      '</div><span class="shrink-0 text-xs font-semibold text-slate-400">' + fmtDate(message.date) + '</span></div></div></div>' +
      '<div class="message-body text-[15px] leading-6 text-slate-700 dark:text-slate-200">' + renderMentionText(message, false) + '</div>' +
      mentionedYouBadge(message, false) +
      renderMessageControls(state, message, false) +
      (!protectedMessage ? renderMessageImages(message, false) + renderAttachmentImages(message, false) : '') +
      (!protectedMessage ? renderMessageControls(state, message, false) : '') +
      (state.boot.isMonita && !protectedMessage ? '<div class="mt-4 flex justify-end gap-2">' +
        (archived
          ? '<button data-restore-message="' + message.id + '" class="rounded-lg bg-monita-50 px-3 py-2 text-xs font-bold text-monita-700 dark:bg-monita-950 dark:text-monita-300">Restore</button>'
          : '<button data-archive-message="' + message.id + '" class="rounded-lg bg-slate-100 px-3 py-2 text-xs font-bold text-slate-600 dark:bg-slate-800 dark:text-slate-300">' + icon("archive", "mr-1 inline h-4 w-4") + ' Archive</button>') +
        (isAdmin
          ? '<button data-delete-message="' + message.id + '" class="rounded-lg bg-red-50 px-3 py-2 text-xs font-bold text-red-700 dark:bg-red-950/40 dark:text-red-300">' + icon("trash", "mr-1 inline h-4 w-4") + ' Delete</button>'
          : '') +
        '</div>' : '') +
      '</article>';
  }

  function inbox(state) {
    var mode = sectionMode(state, "inbox");
    var list = mode === "archived" ? (state.archiveMessages || []) : (state.boot.messages || []);
    list = list.filter(function (m) {
      var channel = channelById(state, m.appid);
      return !isChat(channel);
    });
    return shell(state, '<section class="mb-4 flex items-end justify-between gap-3"><div>' +
      '<h1 class="text-[26px] font-black leading-tight tracking-tight">Inbox</h1>' +
      '<p class="mt-1 text-sm text-slate-500 dark:text-slate-400">Notification messages</p></div>' + modeTabs(state, "inbox") + '</section>' +
      '<section class="space-y-2.5">' + (list.length ? list.map(function (m) { return messageCard(state, m, mode === "archived"); }).join("") :
        emptyState(mode === "archived" ? "No archived notifications" : "You’re all caught up", mode === "archived" ? "Archived notification messages will appear here." : "New notification messages will appear here.")) +
      '</section>');
  }

  function channelCard(state, channel) {
    var chat = isChat(channel);
    var notifications = channel.receiveNotifications !== false;
    var protectedChannel = isProtectedChannel(state, channel.id);
    return '<article class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-[#151a20]">' +
      '<button data-open-channel="' + channel.id + '" class="flex w-full items-center gap-3 p-3.5 text-left">' +
      channelVisual(state, channel, "h-10 w-10") +
      '<div class="min-w-0 flex-1"><div class="truncate text-[15px] font-extrabold">' + esc(channel.name) + '</div>' +
      '<div class="mt-1 flex flex-wrap gap-1.5">' + (protectedChannel ? badge("Protected","amber") : "") + (channel.autoAssign ? badge("Global","amber") : "") + (!notifications ? badge("Muted","neutral") : "") +
      (channel.role ? badge(channel.role, channel.role === "owner" ? "blue" : "neutral") : "") + '</div></div>' + icon("chevron","h-5 w-5 text-slate-400") + '</button>' +
      (state.boot.isMonita ? '<div class="flex items-center justify-between border-t border-slate-100 px-3.5 py-2.5 dark:border-slate-800">' +
      '<div class="text-xs font-semibold text-slate-500 dark:text-slate-400">' + (notifications ? "Notifications on" : "Notifications muted") + '</div>' +
      '<button data-toggle-notifications="' + channel.id + '" data-enabled="' + (notifications ? "false" : "true") + '" class="inline-flex items-center rounded-lg px-2.5 py-1.5 text-xs font-bold ' +
      (notifications ? 'bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300' : 'bg-monita-50 text-monita-800 dark:bg-monita-950 dark:text-monita-300') + '">' +
      (notifications ? icon("belloff","mr-1 inline h-4 w-4") + "Mute" : icon("bell","mr-1 inline h-4 w-4") + "Unmute") + '</button></div>' : '') + '</article>';
  }

  function sectionChannels(state, section, chatOnly) {
    var mode = sectionMode(state, section);
    if (mode === "archived") {
      var archived = (state.archiveMessages || []).filter(function (m) {
        var channel = channelById(state, m.appid);
        return !!channel && isChat(channel) === chatOnly;
      });
      return shell(state, '<section class="mb-4 flex items-end justify-between gap-3"><div><h1 class="text-[26px] font-black leading-tight tracking-tight">' +
        (chatOnly ? 'Chats' : 'Channels') + '</h1><p class="mt-1 text-sm text-slate-500 dark:text-slate-400">' +
        (chatOnly ? 'Two-way conversations' : 'Notification channels') + '</p></div>' + modeTabs(state, section) + '</section>' +
        '<section class="space-y-2.5">' + (archived.length ? archived.map(function (m) { return messageCard(state, m, true); }).join("") :
        emptyState("Nothing archived here", chatOnly ? "Archived chat messages will appear here." : "Archived channel notifications will appear here.")) + '</section>');
    }

    var list = (state.boot.channels || []).filter(function (c) { return isChat(c) === chatOnly; });
    return shell(state, '<section class="mb-4 flex items-end justify-between gap-3"><div><h1 class="text-[26px] font-black leading-tight tracking-tight">' +
      (chatOnly ? 'Chats' : 'Channels') + '</h1><p class="mt-1 text-sm text-slate-500 dark:text-slate-400">' +
      esc(list.length) + (chatOnly ? ' conversations' : ' notification channels') + '</p></div>' + modeTabs(state, section) + '</section>' +
      '<section class="space-y-2.5">' + (list.length ? list.map(function (item) { return channelCard(state, item); }).join("") :
      emptyState(chatOnly ? "No chats" : "No channels", chatOnly ? "Chat channels assigned to you will appear here." : "Notification channels assigned to you will appear here.")) + '</section>');
  }

  function chats(state) { return sectionChannels(state, "chats", true); }
  function channels(state) { return sectionChannels(state, "channels", false); }

  function chatBubble(state, message, ownId, archived) {
    var mine = ownId != null && Number(message.senderUserId) === Number(ownId);
    return '<div class="flex ' + (mine ? 'justify-end' : 'justify-start') + '"><div class="max-w-[86%] rounded-xl px-4 py-3 ' +
      (mine ? 'rounded-br-md bg-monita-600 text-white' : 'rounded-bl-md border border-slate-200 bg-white text-slate-800 dark:border-slate-800 dark:bg-[#151a20] dark:text-slate-100') + '">' +
      (!mine ? '<div class="mb-1 text-[11px] font-extrabold uppercase tracking-wide text-monita-600 dark:text-monita-400">' + esc(message.senderName || message.title || "Member") + '</div>' : '') +
      '<div class="message-body text-[15px] leading-6">' + renderMentionText(message, mine) + '</div>' +
      mentionedYouBadge(message, mine) +
      renderMessageControls(state, message, mine) +
      renderMessageImages(message, true) + renderAttachmentImages(message, true) +
      renderMessageControls(state, message, mine) +
      '<div class="mt-1.5 flex items-center justify-end gap-2 text-[10px] font-semibold ' + (mine ? 'text-white/70' : 'text-slate-400') + '">' +
      '<span>' + fmtDate(message.date) + '</span>' +
      (archived ? '<button data-restore-message="' + message.id + '" class="' + (mine ? 'text-white' : 'text-monita-600 dark:text-monita-300') + '">Restore</button>' :
        '<button data-archive-message="' + message.id + '" class="' + (mine ? 'text-white' : 'text-slate-500') + '">Archive</button>') +
      '</div></div></div>';
  }

  function channel(state) {
    var selected = channelById(state, state.selectedChannelId);
    if (!selected) return state.channelReturnTab === "chats" ? chats(state) : channels(state);
    var chat = isChat(selected);
    var ownId = state.boot.user && state.boot.user.id;
    var archived = !!state.channelArchived;
    var protectedChannel = isProtectedChannel(state, selected.id);
    var stream = state.channelMessages.length
      ? state.channelMessages.map(function (m) { return chat ? chatBubble(state, m, ownId, archived) : messageCard(state, m, archived); }).join("")
      : emptyState(archived ? "Nothing archived" : (chat ? "Start the conversation" : "No messages"),
          archived ? "Archived messages for this " + (chat ? "chat" : "channel") + " will appear here." :
          (chat ? "Messages sent here are shared with channel members." : "Notifications for this channel will appear here."));

    var composer = "";
    if (chat && !archived) {
      var imagePreviews = (state.chatImages || []).map(function (image, index) {
        return '<div class="relative h-20 w-20 shrink-0 overflow-hidden rounded-lg border border-slate-200 bg-slate-100 dark:border-slate-700 dark:bg-slate-900">' +
          '<img src="' + esc(image.preview) + '" alt="' + esc(image.filename || "Selected photo") + '" class="h-full w-full object-cover">' +
          '<button type="button" data-chat-image-remove="' + index + '" class="absolute right-1 top-1 flex h-6 w-6 items-center justify-center rounded-full bg-black/70 text-sm font-black text-white" aria-label="Remove photo">×</button></div>';
      }).join("");
      var imageControls = chatImagesSupported(state)
        ? '<input id="chat-image-input" type="file" accept="image/jpeg,image/png,image/gif,image/webp" multiple class="hidden">' +
          '<button type="button" data-chat-image-add class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg text-slate-500 active:bg-slate-100 dark:text-slate-300 dark:active:bg-slate-800" aria-label="Add image or GIF">' + icon("paperclip") + '</button>'
        : '';
      composer = '<div class="safe-bottom fixed inset-x-0 bottom-[64px] z-40 border-t border-slate-200 glass dark:border-slate-800"><div class="mx-auto max-w-3xl px-3 pb-3 pt-2">' +
        '<div id="mention-suggestions" class="mb-2 hidden max-h-44 overflow-y-auto rounded-xl border border-slate-200 bg-white shadow-lg dark:border-slate-700 dark:bg-[#151a20]"></div>' +
        '<div id="typing-indicator" class="mb-1.5 flex min-h-[18px] items-center gap-1.5 px-2 text-xs font-semibold text-slate-500 opacity-0 transition-opacity dark:text-slate-400"></div>' +
        (canPost(selected)
          ? '<form id="chat-form" class="rounded-xl border border-slate-300 bg-white p-2 shadow-sm focus-within:border-monita-500 focus-within:ring-2 focus-within:ring-monita-100 dark:border-slate-700 dark:bg-[#151a20] dark:focus-within:border-monita-400 dark:focus-within:ring-monita-950">' +
            (imagePreviews ? '<div class="mb-2 flex gap-2 overflow-x-auto pb-1">' + imagePreviews + '</div>' : '') +
            '<div class="flex items-end gap-1">' + imageControls + '<textarea id="chat-input" rows="1" maxlength="4000" placeholder="Message ' + esc(selected.name) + '" class="max-h-32 min-h-11 flex-1 resize-none bg-transparent px-2 py-2.5 text-[15px] outline-none placeholder:text-slate-400">' + esc(state.chatDraft || "") + '</textarea><button class="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-monita-700 text-white active:bg-monita-800 disabled:opacity-50" type="submit">' + icon("send") + '</button></div></form>'
          : '<div class="rounded-lg bg-slate-100 px-4 py-3 text-center text-sm font-semibold text-slate-500 dark:bg-slate-800 dark:text-slate-400">This conversation is read-only for your account.</div>') +
        '</div></div>';
    }

    return shell(state, '<section class="mb-4 flex items-start gap-3"><button data-action="back-channel-list" class="mt-0.5 rounded-lg bg-slate-100 p-2.5 text-slate-600 dark:bg-slate-800 dark:text-slate-300">' + icon("back") + '</button>' +
      '<div class="min-w-0 flex-1"><div class="flex flex-wrap items-center gap-2"><h1 class="truncate text-xl font-black tracking-tight">' + esc(selected.name) + '</h1>' +
      badge(chat ? "Chat" : "Notifications","blue") + (protectedChannel ? badge("Protected","amber") : "") + '</div><div class="mt-1 text-xs text-slate-500 dark:text-slate-400">' + esc(selected.description || (chat ? "Two-way conversation" : "Notification channel")) + '</div>' +
      '<div class="mt-3 flex flex-wrap items-center gap-2">' + modeTabs({viewModes:{channel: archived ? "archived" : "active"}}, "channel") +
      '<button data-channel-protection="' + selected.id + '" data-protected="' + (protectedChannel ? "true" : "false") + '" class="rounded-lg border border-slate-200 px-3 py-2 text-xs font-extrabold text-slate-700 dark:border-slate-700 dark:text-slate-200">' +
      icon("lock","mr-1 inline h-4 w-4") + (protectedChannel ? "Unprotect" : "Protect") + '</button></div></div></section>' +
      '<section class="' + (chat ? 'space-y-2 ' : 'space-y-3 ') + (chat && !archived && canPost(selected) ? 'pb-28' : 'pb-2') + '">' + stream + '</section>' + composer);
  }

  function auditLabel(event) {
    var target = String(event.target || "").toLowerCase();
    var action = String(event.action || "").toLowerCase();
    if (target.indexOf("/client/:id/elevate") >= 0) return "Elevated client session";
    if (target.indexOf("/application/:id/owner") >= 0) return "Transferred channel ownership";
    if (target.indexOf("/application/:id/members") >= 0) return "Changed channel membership";
    if (target.indexOf("/group") >= 0) return "Changed group configuration";
    if (target.indexOf("/user") >= 0) return "Changed user account";
    if (target.indexOf("/application") >= 0 && action === "put") return "Updated channel";
    if (target === "/application" && action === "post") return "Created channel";
    return (action ? action.toUpperCase() + " " : "") + (event.target || "Administrative action");
  }

  function adminNav(state) {
    var items = [["overview","Overview","settings"],["users","Users","users"],["groups","Groups","group"],["channels","Channels","channels"],["audit","Audit","audit"]];
    return '<div class="mb-5 flex gap-1 overflow-x-auto rounded-xl bg-slate-100 p-1 dark:bg-slate-800">' +
      items.map(function (item) {
        var active = state.adminSection === item[0];
        return '<button data-admin-section="' + item[0] + '" class="flex shrink-0 items-center gap-1.5 rounded-lg px-3 py-2 text-xs font-extrabold ' +
          (active ? 'bg-white text-monita-700 shadow-sm dark:bg-slate-700 dark:text-monita-300' : 'text-slate-500 dark:text-slate-400') + '">' +
          icon(item[2],"h-4 w-4") + item[1] + '</button>';
      }).join("") + '</div>';
  }

  function adminOverview(state) {
    var a = state.admin;
    function card(section, iconName, title, body, count) {
      return '<button data-admin-section="' + section + '" class="flex w-full items-center gap-3 rounded-xl border border-slate-200 bg-white p-4 text-left dark:border-slate-800 dark:bg-[#151a20]">' +
        '<div class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-monita-50 text-monita-700 dark:bg-monita-950 dark:text-monita-300">' + icon(iconName) + '</div>' +
        '<div class="min-w-0 flex-1"><div class="flex items-center justify-between gap-3"><div class="text-sm font-extrabold">' + title + '</div><div class="text-lg font-black">' + esc(count) + '</div></div>' +
        '<div class="mt-1 text-xs leading-5 text-slate-500 dark:text-slate-400">' + body + '</div></div>' + icon("chevron","h-5 w-5 text-slate-400") + '</button>';
    }
    return '<section class="grid grid-cols-3 gap-2 mb-5">' +
      statCard((a.users || []).length,"Users") + statCard((a.groups || []).length,"Groups") + statCard((a.channels || []).length,"Channels") +
      '</section><section class="space-y-2.5">' +
      card("users","users","Users","Create accounts, edit identity and admin access, reset passwords, or remove users.",(a.users||[]).length) +
      card("groups","group","Groups","Create groups and manage which users belong to each group.",(a.groups||[]).length) +
      card("channels","channels","Channels","Create or edit channels, roles, memberships, groups, ownership, global assignment, and posting.",(a.channels||[]).length) +
      card("audit","audit","Audit","Review administrative and security activity.",(a.audit||[]).length) + '</section>';
  }

  function adminUsers(state) {
    var rows = (state.admin.users || []).map(function (u) {
      return '<div class="flex items-center gap-3 border-t border-slate-100 px-3.5 py-3 first:border-t-0 dark:border-slate-800">' +
        '<div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-[11px] font-black text-slate-600 dark:bg-slate-800 dark:text-slate-300">' +
        esc((u.displayName || u.name || "?").slice(0,2).toUpperCase()) + '</div>' +
        '<div class="min-w-0 flex-1"><div class="truncate text-sm font-extrabold">' + esc(u.displayName || u.name) + '</div><div class="truncate text-xs text-slate-400">@' + esc(u.name) + '</div></div>' +
        (u.admin ? badge("Admin","blue") : badge("User","neutral")) +
        '<button data-admin-user-edit="' + u.id + '" class="rounded-lg p-2 text-slate-500 active:bg-slate-100 dark:active:bg-slate-800" aria-label="Edit user">' + icon("edit","h-4 w-4") + '</button>' +
        '<button data-admin-user-delete="' + u.id + '" class="rounded-lg p-2 text-red-500 active:bg-red-50 dark:active:bg-red-950/40" aria-label="Delete user">' + icon("trash","h-4 w-4") + '</button></div>';
    }).join("");
    return '<section class="mb-3 flex items-center justify-between gap-3"><div><h2 class="text-xl font-black">Users</h2><p class="text-xs text-slate-500 dark:text-slate-400">Account and administrator access</p></div>' +
      '<button data-admin-user-add class="inline-flex items-center gap-1.5 rounded-lg bg-monita-700 px-3 py-2 text-xs font-extrabold text-white">' + icon("plus","h-4 w-4") + 'Add user</button></section>' +
      '<div class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-[#151a20]">' + (rows || '<div class="p-4">' + emptyState("No users","") + '</div>') + '</div>';
  }

  function adminGroups(state) {
    var rows = (state.admin.groups || []).map(function (g) {
      return '<div class="rounded-xl border border-slate-200 bg-white p-3.5 dark:border-slate-800 dark:bg-[#151a20]"><div class="flex items-start gap-3">' +
        '<div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-monita-50 text-monita-700 dark:bg-monita-950 dark:text-monita-300">' + icon("group","h-4 w-4") + '</div>' +
        '<div class="min-w-0 flex-1"><div class="text-sm font-extrabold">' + esc(g.name) + '</div><div class="mt-0.5 text-xs text-slate-500 dark:text-slate-400">' + esc(g.description || "No description") + '</div><div class="mt-1 text-[11px] font-bold text-slate-400">' + esc(g.memberCount) + ' members</div></div>' +
        '<button data-admin-group-edit="' + g.id + '" class="rounded-lg p-2 text-slate-500">' + icon("edit","h-4 w-4") + '</button>' +
        '<button data-admin-group-delete="' + g.id + '" class="rounded-lg p-2 text-red-500">' + icon("trash","h-4 w-4") + '</button></div>' +
        '<button data-admin-group-members="' + g.id + '" class="mt-3 w-full rounded-lg bg-slate-100 px-3 py-2 text-xs font-extrabold text-slate-700 dark:bg-slate-800 dark:text-slate-200">Manage members</button></div>';
    }).join("");
    return '<section class="mb-3 flex items-center justify-between gap-3"><div><h2 class="text-xl font-black">Groups</h2><p class="text-xs text-slate-500 dark:text-slate-400">Reusable user membership groups</p></div>' +
      '<button data-admin-group-add class="inline-flex items-center gap-1.5 rounded-lg bg-monita-700 px-3 py-2 text-xs font-extrabold text-white">' + icon("plus","h-4 w-4") + 'Add group</button></section>' +
      '<section class="space-y-2.5">' + (rows || emptyState("No groups","Create a group to manage users together.")) + '</section>';
  }

  function adminChannelCard(state, channel) {
    return '<div class="rounded-xl border border-slate-200 bg-white p-3.5 dark:border-slate-800 dark:bg-[#151a20]"><div class="flex items-start gap-3">' +
      channelVisual(state, channel, "h-9 w-9") +
      '<div class="min-w-0 flex-1"><div class="flex flex-wrap items-center gap-1.5"><div class="truncate text-sm font-extrabold">' + esc(channel.name) + '</div>' + badge(isChat(channel) ? "Chat" : "Notifications","blue") + (channel.autoAssign ? badge("Global","amber") : "") + '</div>' +
      '<div class="mt-1 text-xs text-slate-500 dark:text-slate-400">' + esc(channel.description || "No description") + '</div></div>' +
      '<button data-admin-channel-edit="' + channel.id + '" class="rounded-lg p-2 text-slate-500">' + icon("edit","h-4 w-4") + '</button>' +
      '<button data-admin-channel-delete="' + channel.id + '" class="rounded-lg p-2 text-red-500">' + icon("trash","h-4 w-4") + '</button></div>' +
      '<div class="mt-3 grid grid-cols-3 gap-2"><button data-admin-channel-manage="' + channel.id + '" class="rounded-lg bg-monita-50 px-2.5 py-2 text-xs font-extrabold text-monita-800 dark:bg-monita-950 dark:text-monita-300">Members</button>' +
      '<button data-admin-autoassign="' + channel.id + '" data-enabled="' + (channel.autoAssign ? "false" : "true") + '" class="rounded-lg bg-slate-100 px-2.5 py-2 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-200">' + (channel.autoAssign ? "Global on" : "Global off") + '</button>' +
      '<button data-admin-posting="' + channel.id + '" data-enabled="' + (channel.allowMemberPost ? "false" : "true") + '" class="rounded-lg bg-slate-100 px-2.5 py-2 text-xs font-bold text-slate-700 dark:bg-slate-800 dark:text-slate-200">' + (channel.allowMemberPost ? "Posting on" : "Posting off") + '</button></div></div>';
  }

  function adminChannels(state) {
    var rows = (state.admin.channels || []).map(function (channel) { return adminChannelCard(state, channel); }).join("");
    return '<section class="mb-3 flex items-center justify-between gap-3"><div><h2 class="text-xl font-black">Channels</h2><p class="text-xs text-slate-500 dark:text-slate-400">Structure, access and delivery</p></div>' +
      '<button data-admin-channel-add class="inline-flex items-center gap-1.5 rounded-lg bg-monita-700 px-3 py-2 text-xs font-extrabold text-white">' + icon("plus","h-4 w-4") + 'Add channel</button></section>' +
      '<section class="space-y-2.5">' + (rows || emptyState("No channels","Create a notification channel or chat.")) + '</section>';
  }

  function adminAudit(state) {
    var rows = (state.admin.audit || []).map(function (ev, i) {
      return '<div class="px-3.5 py-3 ' + (i ? 'border-t border-slate-100 dark:border-slate-800' : '') + '"><div class="flex items-start justify-between gap-3"><div class="min-w-0 flex-1">' +
        '<div class="text-sm font-bold">' + esc(auditLabel(ev)) + '</div><div class="mt-0.5 break-all text-[11px] text-slate-500 dark:text-slate-400">' +
        esc(ev.username || "system") + (ev.target ? ' · ' + esc(ev.target) : '') + (ev.targetId ? ' · ' + esc(ev.targetId) : '') + '</div></div>' +
        '<div class="shrink-0 pt-0.5 text-[11px] font-semibold text-slate-400">' + fmtDate(ev.createdAt) + '</div></div></div>';
    }).join("");
    return '<section class="mb-3"><h2 class="text-xl font-black">Audit</h2><p class="text-xs text-slate-500 dark:text-slate-400">Administrative and security history is intentionally read-only.</p></section>' +
      '<div class="overflow-hidden rounded-xl border border-slate-200 bg-white dark:border-slate-800 dark:bg-[#151a20]">' + (rows || '<div class="p-4">' + emptyState("No audit events","") + '</div>') + '</div>';
  }

  function admin(state) {
    var boot = state.boot;
    var adminAccount = !!((boot.user && boot.user.admin) || (boot.settingsUser && boot.settingsUser.admin));
    if (!adminAccount || !boot.isMonita) return shell(state, emptyState("Admin unavailable","This account or server does not expose Monita administration."));
    if (state.adminNeedsElevation || !state.admin) {
      return shell(state, '<section class="mx-auto max-w-md pt-2"><div class="mb-4 flex h-11 w-11 items-center justify-center rounded-lg bg-monita-50 text-monita-700 dark:bg-monita-950 dark:text-monita-300">' + icon("lock","h-5 w-5") + '</div>' +
        '<h1 class="text-[26px] font-black leading-tight tracking-tight">Unlock Admin</h1>' +
        '<p class="mt-2 text-sm leading-5 text-slate-500 dark:text-slate-400">Admin actions are elevated for 15 minutes. Your password goes directly to your Monita server and is not saved.</p>' +
        '<form id="admin-unlock-form" class="mt-5 space-y-3"><input id="admin-password" type="password" autocomplete="current-password" placeholder="Admin password" class="w-full rounded-lg border border-slate-300 bg-white px-3.5 py-3 text-[15px] outline-none focus:border-monita-500 focus:ring-2 focus:ring-monita-100 dark:border-slate-700 dark:bg-[#151a20] dark:focus:ring-monita-950">' +
        '<button type="submit" class="w-full rounded-lg bg-monita-700 px-4 py-3 text-sm font-extrabold text-white active:bg-monita-800">Unlock Admin</button></form></section>');
    }

    var body = state.adminSection === "users" ? adminUsers(state)
      : state.adminSection === "groups" ? adminGroups(state)
      : state.adminSection === "channels" ? adminChannels(state)
      : state.adminSection === "audit" ? adminAudit(state)
      : adminOverview(state);

    return shell(state, '<section class="mb-4 flex items-center justify-between gap-3"><div><h1 class="text-[26px] font-black leading-tight tracking-tight">Admin</h1><p class="mt-1 text-sm text-slate-500 dark:text-slate-400">Server administration</p></div>' + badge("Elevated","blue") + '</section>' + adminNav(state) + body);
  }

  window.MonitaViews = {
    esc: esc,
    isChat: isChat,
    isProtectedChannel: isProtectedChannel,
    safeImageUrl: safeImageUrl,
    channelImageUrl: channelImageUrl,
    imageItems: imageItems,
    renderMessageImages: renderMessageImages,
    attachmentImages: attachmentImages,
    renderAttachmentImages: renderAttachmentImages,
    configuredMessageControls: configuredMessageControls,
    renderMessageControls: renderMessageControls,
    chatImagesSupported: chatImagesSupported,
    loading: loading,
    inbox: inbox,
    chats: chats,
    channels: channels,
    channel: channel,
    admin: admin
  };
})();