(function () {
  "use strict";

  var V = window.MonitaViews;
  var root = document.getElementById("app");
  var state = {
    boot: null,
    tab: "inbox",
    selectedChannelId: null,
    channelReturnTab: "channels",
    channelArchived: false,
    channelMessages: [],
    archiveMessages: [],
    viewModes: {inbox: "active", chats: "active", channels: "active"},
    admin: null,
    adminSection: "overview",
    adminNeedsElevation: false,
    typingByChannel: {},
    mentionableUsers: [],
    chatDraft: "",
    chatImages: [],
    attachmentImageCache: new Map(),
    busy: false,
    toast: null,
    theme: localStorage.getItem("monita-theme") || localStorage.getItem("mu-theme") || "system",
    pending: new Map(),
    seq: 0
  };

  var typingStopTimer = null;
  var lastTypingSentAt = 0;
  var autoSyncTimer = null;
  var autoSyncRunning = false;
  var AUTO_SYNC_MS = 12000;

  function applyTheme() {
    var dark = state.theme === "dark" ||
      (state.theme === "system" && matchMedia("(prefers-color-scheme: dark)").matches);
    document.documentElement.classList.toggle("dark", dark);
    document.documentElement.style.colorScheme = dark ? "dark" : "light";
  }

  function cycleTheme() {
    state.theme = state.theme === "system" ? "dark" : state.theme === "dark" ? "light" : "system";
    localStorage.setItem("monita-theme", state.theme);
    applyTheme();
    render();
  }

  function native(action, payload) {
    payload = payload || {};
    return new Promise(function (resolve, reject) {
      var bridge = window.MonitaNative;
      if (!bridge || !bridge.request) {
        reject({code: 0, message: "Native bridge unavailable"});
        return;
      }
      var id = "r" + (++state.seq);
      state.pending.set(id, {resolve: resolve, reject: reject});
      bridge.request(action, JSON.stringify(payload), id);
    });
  }

  function typingSupported() {
    return !!(state.boot && state.boot.capabilities && state.boot.capabilities.features &&
      state.boot.capabilities.features.typingPresence);
  }

  function mentionsSupported() {
    return !!(state.boot && state.boot.capabilities && state.boot.capabilities.features &&
      state.boot.capabilities.features.mentions);
  }

  function selectedChannel() {
    return (state.boot && state.boot.channels || []).find(function (c) {
      return Number(c.id) === Number(state.selectedChannelId);
    });
  }

  async function loadMentionableUsers() {
    state.mentionableUsers = [];
    if (!mentionsSupported() || !state.selectedChannelId || state.channelArchived) return;
    try {
      state.mentionableUsers = await native("mentionableUsers", {appId: state.selectedChannelId}) || [];
    } catch (_) {
      state.mentionableUsers = [];
    }
  }

  function currentMentionQuery(input) {
    if (!input) return null;
    var pos = input.selectionStart == null ? input.value.length : input.selectionStart;
    var before = input.value.slice(0, pos);
    var match = before.match(/(?:^|\s|[([{,;:!?])@([A-Za-z0-9._-]*)$/);
    if (!match) return null;
    return {query: match[1].toLowerCase(), start: before.length - match[1].length - 1, end: pos};
  }

  function updateMentionSuggestions(input) {
    var box = document.getElementById("mention-suggestions");
    if (!box) return;
    var mention = currentMentionQuery(input);
    if (!mention || !state.mentionableUsers.length) {
      box.classList.add("hidden");
      box.innerHTML = "";
      return;
    }
    var matches = state.mentionableUsers.filter(function (user) {
      var name = String(user.name || "").toLowerCase();
      var display = String(user.displayName || "").toLowerCase();
      return !mention.query || name.indexOf(mention.query) === 0 || display.indexOf(mention.query) === 0;
    }).slice(0, 6);
    if (!matches.length) {
      box.classList.add("hidden");
      box.innerHTML = "";
      return;
    }
    box.innerHTML = matches.map(function (user) {
      var label = user.displayName || user.name;
      return '<button type="button" data-mention-user="' + V.esc(user.name) + '" class="flex w-full items-center gap-3 border-t border-slate-100 px-3 py-2.5 text-left first:border-t-0 active:bg-slate-50 dark:border-slate-800 dark:active:bg-slate-800">' +
        '<div class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-monita-50 text-[11px] font-black text-monita-700 dark:bg-monita-950 dark:text-monita-300">' + V.esc(String(user.name || "?").slice(0,2).toUpperCase()) + '</div>' +
        '<div class="min-w-0 flex-1"><div class="truncate text-sm font-bold">' + V.esc(label) + '</div><div class="truncate text-xs text-slate-500 dark:text-slate-400">@' + V.esc(user.name) + '</div></div></button>';
    }).join("");
    box.classList.remove("hidden");
    box.querySelectorAll("[data-mention-user]").forEach(function (button) {
      button.addEventListener("click", function () {
        var active = currentMentionQuery(input);
        if (!active) return;
        var replacement = "@" + button.dataset.mentionUser + " ";
        input.value = input.value.slice(0, active.start) + replacement + input.value.slice(active.end);
        var caret = active.start + replacement.length;
        input.setSelectionRange(caret, caret);
        box.classList.add("hidden");
        box.innerHTML = "";
        input.focus();
        input.dispatchEvent(new Event("input", {bubbles: true}));
      });
    });
  }

  function setTypingLocalEvent(event) {
    if (!event || event.type !== "typing") return;
    var channelKey = String(event.applicationId);
    if (!state.typingByChannel[channelKey]) state.typingByChannel[channelKey] = {};
    if (event.typing && new Date(event.expiresAt).getTime() > Date.now()) {
      state.typingByChannel[channelKey][String(event.userId)] = {
        name: event.userName || "Someone",
        expiresAt: event.expiresAt
      };
      var delay = Math.max(250, new Date(event.expiresAt).getTime() - Date.now() + 100);
      setTimeout(function () {
        pruneTyping(channelKey);
        updateTypingIndicator();
      }, delay);
    } else {
      delete state.typingByChannel[channelKey][String(event.userId)];
    }
    updateTypingIndicator();
  }

  function pruneTyping(channelKey) {
    var entries = state.typingByChannel[channelKey] || {};
    Object.keys(entries).forEach(function (userId) {
      if (new Date(entries[userId].expiresAt).getTime() <= Date.now()) delete entries[userId];
    });
  }

  function updateTypingIndicator() {
    if (state.tab !== "channel" || !state.selectedChannelId || state.channelArchived) return;
    var key = String(state.selectedChannelId);
    pruneTyping(key);
    var el = document.getElementById("typing-indicator");
    if (!el) return;
    var names = Object.values(state.typingByChannel[key] || {}).map(function (x) { return x.name; });
    var text = names.length === 1 ? names[0] + " is typing"
      : names.length === 2 ? names[0] + " and " + names[1] + " are typing"
      : names.length > 2 ? names.length + " people are typing" : "";
    el.innerHTML = text
      ? '<span>' + V.esc(text) + '</span><span class="typing-dots"><span></span><span></span><span></span></span>'
      : "";
    el.classList.toggle("opacity-0", !text);
  }

  function sendTyping(active) {
    if (!typingSupported() || !state.selectedChannelId || state.channelArchived) return;
    native("typing", {appId: state.selectedChannelId, typing: !!active}).catch(function () {});
    if (!active) lastTypingSentAt = 0;
  }

  var monitaBridge = {
    nativeResult: function (id, envelope) {
      var pending = state.pending.get(id);
      if (!pending) return;
      state.pending.delete(id);
      if (envelope && envelope.ok) pending.resolve(envelope.data);
      else pending.reject((envelope && envelope.error) || {message: "Request failed"});
    },
    nativeEvent: function (name, payload) {
      if (name === "message") {
        if (state.tab === "channel" && !state.channelArchived &&
            Number(payload && payload.appid) === Number(state.selectedChannelId)) {
          loadChannel(state.selectedChannelId, false);
        } else if (state.tab === "inbox" && state.viewModes.inbox === "active") {
          refreshBootstrap(false);
        }
      } else if (name === "monitaEvent") {
        setTypingLocalEvent(payload);
      } else if (name === "openChannel" && payload && payload.channelId) {
        openChannel(Number(payload.channelId), false);
      } else if (name === "resume" && state.boot) {
        refreshCurrent();
      }
    },
    back: function () {
      if (state.tab === "channel") {
        leaveChannel();
        return true;
      }
      if (state.tab === "admin" && state.adminSection !== "overview") {
        state.adminSection = "overview";
        render();
        return true;
      }
      return false;
    }
  };
  window.Monita = monitaBridge;
  window.MonitaApp = monitaBridge;

  function showToast(message, tone) {
    state.toast = {message: message, tone: tone || "normal"};
    render();
    setTimeout(function () {
      if (state.toast && state.toast.message === message) {
        state.toast = null;
        render();
      }
    }, 2600);
  }

  function closeOverlay() {
    var node = document.getElementById("monita-overlay");
    if (node) node.remove();
  }

  function showConfirmDialog(title, message, confirmLabel) {
    return new Promise(function (resolve) {
      closeOverlay();
      var overlay = document.createElement("div");
      overlay.id = "monita-overlay";
      overlay.className = "fixed inset-0 z-[80] flex items-end bg-black/45 p-3 sm:items-center sm:justify-center";
      overlay.innerHTML =
        '<div class="w-full max-w-md rounded-xl border border-slate-200 bg-white p-4 shadow-2xl dark:border-slate-700 dark:bg-[#151a20]">' +
        '<div class="text-base font-extrabold">' + V.esc(title) + '</div>' +
        '<div class="mt-1.5 text-sm leading-5 text-slate-500 dark:text-slate-400">' + V.esc(message) + '</div>' +
        '<div class="mt-4 grid grid-cols-2 gap-2"><button data-dialog-cancel class="rounded-lg border border-slate-200 px-3 py-2.5 text-sm font-bold text-slate-600 dark:border-slate-700 dark:text-slate-300">Cancel</button>' +
        '<button data-dialog-confirm class="rounded-lg bg-monita-700 px-3 py-2.5 text-sm font-extrabold text-white">' + V.esc(confirmLabel || "Confirm") + '</button></div></div>';
      document.body.appendChild(overlay);
      function done(value) { closeOverlay(); resolve(value); }
      overlay.querySelector("[data-dialog-cancel]").addEventListener("click", function () { done(false); });
      overlay.querySelector("[data-dialog-confirm]").addEventListener("click", function () { done(true); });
      overlay.addEventListener("click", function (event) { if (event.target === overlay) done(false); });
    });
  }

  function readImageFile(file) {
    if (!file) return Promise.resolve(null);
    var allowed = ["image/png", "image/jpeg", "image/gif"];
    if (file.size > 5 * 1024 * 1024) {
      return Promise.reject(new Error("Channel image must be 5 MB or smaller."));
    }
    if (file.type && allowed.indexOf(file.type) === -1) {
      return Promise.reject(new Error("Channel image must be PNG, JPG, JPEG, or GIF."));
    }
    if (!/\.(png|jpe?g|gif)$/i.test(file.name || "")) {
      return Promise.reject(new Error("Channel image filename must end in PNG, JPG, JPEG, or GIF."));
    }
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onerror = function () { reject(new Error("Could not read the selected Channel image.")); };
      reader.onload = function () {
        var value = String(reader.result || "");
        var comma = value.indexOf(",");
        if (comma < 0) {
          reject(new Error("Could not encode the selected Channel image."));
          return;
        }
        resolve({
          filename:file.name,
          mimeType:file.type || "",
          size:file.size,
          base64:value.slice(comma + 1)
        });
      };
      reader.readAsDataURL(file);
    });
  }

  function readChatImageFile(file) {
    if (!file) return Promise.resolve(null);
    var allowed = ["image/png", "image/jpeg", "image/gif", "image/webp"];
    var extension = String(file.name || "").split(".").pop().toLowerCase();
    var inferred = extension === "png" ? "image/png" : extension === "gif" ? "image/gif" : extension === "webp" ? "image/webp" : (extension === "jpg" || extension === "jpeg") ? "image/jpeg" : "";
    var mimeType = file.type || inferred;
    if (file.size <= 0 || file.size > 25 * 1024 * 1024) {
      return Promise.reject(new Error(file.name + ": each photo must be 25 MiB or smaller."));
    }
    if (allowed.indexOf(mimeType) === -1) {
      return Promise.reject(new Error(file.name + ": only JPEG, PNG, GIF, and WebP images are supported."));
    }
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onerror = function () { reject(new Error("Could not read " + file.name + ".")); };
      reader.onload = function () {
        var value = String(reader.result || "");
        var comma = value.indexOf(",");
        if (comma < 0) { reject(new Error("Could not encode " + file.name + ".")); return; }
        resolve({
          filename:file.name,
          mimeType:mimeType,
          size:file.size,
          preview:value
        });
      };
      reader.readAsDataURL(file);
    });
  }

  function readMessageAttachmentFile(file) {
    if (!file) return Promise.resolve(null);
    if (file.size <= 0 || file.size > 25 * 1024 * 1024) {
      return Promise.reject(new Error("Attachments must be between 1 byte and 25 MiB."));
    }
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onerror = function () { reject(new Error("Could not read " + (file.name || "attachment") + ".")); };
      reader.onload = function () {
        var value = String(reader.result || "");
        var comma = value.indexOf(",");
        if (comma < 0) {
          reject(new Error("Could not encode the selected attachment."));
          return;
        }
        resolve({
          filename:file.name || "attachment",
          mimeType:file.type || "application/octet-stream",
          size:file.size,
          base64:value.slice(comma + 1)
        });
      };
      reader.readAsDataURL(file);
    });
  }

  function chooseMessageAttachment(messageId) {
    var input = document.createElement("input");
    input.type = "file";
    input.className = "hidden";
    input.addEventListener("change", async function () {
      var file = input.files && input.files[0];
      input.remove();
      if (!file) return;
      await runAction(async function () {
        var attachment = await readMessageAttachmentFile(file);
        if (!attachment) return;
        await native("attachToMessage", {
          messageId:Number(messageId),
          filename:attachment.filename,
          mimeType:attachment.mimeType,
          size:attachment.size,
          base64:attachment.base64
        });
        state.attachmentImageCache.clear();
        if (state.tab === "channel") await loadChannel(state.selectedChannelId, false);
        else await refreshBootstrap(false);
        showToast("Attachment added");
      });
    });
    document.body.appendChild(input);
    input.click();
  }

  function readMessageAttachment(file) {
    if (!file) return Promise.resolve(null);
    if (file.size <= 0 || file.size > 25 * 1024 * 1024) {
      return Promise.reject(new Error("Attachment must be between 1 byte and 25 MiB."));
    }
    return new Promise(function (resolve, reject) {
      var reader = new FileReader();
      reader.onerror = function () { reject(new Error("Could not read " + file.name + ".")); };
      reader.onload = function () {
        var value = String(reader.result || "");
        var comma = value.indexOf(",");
        if (comma < 0) {
          reject(new Error("Could not encode " + file.name + "."));
          return;
        }
        resolve({
          filename:file.name,
          mimeType:file.type || "application/octet-stream",
          base64:value.slice(comma + 1)
        });
      };
      reader.readAsDataURL(file);
    });
  }

  async function addChatImages(files) {
    var list = Array.from(files || []);
    if (!list.length) return;
    var next = state.chatImages.slice();
    var total = next.reduce(function (sum, image) { return sum + Number(image.size || 0); }, 0);
    try {
      for (var i = 0; i < list.length; i += 1) {
        if (next.length >= 8) throw new Error("A Chat message can include at most 8 images.");
        var image = await readChatImageFile(list[i]);
        if (!image) continue;
        if (total + image.size > 50 * 1024 * 1024) {
          throw new Error("Images in one Chat message may total at most 50 MiB.");
        }
        total += image.size;
        next.push(image);
      }
      state.chatImages = next;
      render();
    } catch (error) {
      state.chatImages = next;
      showToast((error && error.message) || "Could not add image", "error");
    }
  }

  function removeChatImage(index) {
    state.chatImages = state.chatImages.filter(function (_image, imageIndex) { return imageIndex !== index; });
    render();
  }

  function secureImageDataUrl(contentType, base64) {
    if (!/^image\/(jpeg|png|gif|webp)$/i.test(String(contentType || ""))) return null;
    if (!/^[A-Za-z0-9+/=\r\n]+$/.test(String(base64 || ""))) return null;
    return "data:" + contentType + ";base64," + String(base64).replace(/[\r\n]/g, "");
  }

  function loadSecureMessageImage(image) {
    if (!image || image.dataset.secureImageLoaded === "true") return;
    var appId = Number(image.dataset.appId);
    var messageId = Number(image.dataset.messageId);
    var attachmentId = Number(image.dataset.attachmentId);
    if (!appId || !messageId || !attachmentId) return;
    var key = messageId + ":" + attachmentId;
    var cached = state.attachmentImageCache.get(key);
    var holder = image.parentElement;
    var loading = holder && holder.querySelector("[data-secure-image-loading]");
    function apply(url) {
      if (!url) return;
      image.src = url;
      image.classList.remove("hidden");
      image.dataset.secureImageLoaded = "true";
      if (loading) loading.classList.add("hidden");
    }
    if (cached) { apply(cached); return; }
    image.dataset.secureImageLoaded = "loading";
    native("attachmentImage", {appId:appId, messageId:messageId, attachmentId:attachmentId})
      .then(function (result) {
        var url = result && secureImageDataUrl(result.contentType, result.base64);
        if (!url) throw new Error("Invalid image response");
        state.attachmentImageCache.set(key, url);
        apply(url);
      })
      .catch(function () {
        image.dataset.secureImageLoaded = "error";
        if (loading) loading.textContent = "Photo unavailable";
      });
  }

  function hydrateSecureMessageImages() {
    var images = Array.from(document.querySelectorAll("img[data-secure-message-image]"));
    if (!images.length) return;
    if (!("IntersectionObserver" in window)) {
      images.forEach(loadSecureMessageImage);
      return;
    }
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        observer.unobserve(entry.target);
        loadSecureMessageImage(entry.target);
      });
    }, {rootMargin:"240px"});
    images.forEach(function (image) { observer.observe(image); });
  }
  function showFormSheet(title, fields, submitLabel) {
    return new Promise(function (resolve) {
      closeOverlay();
      var overlay = document.createElement("div");
      overlay.id = "monita-overlay";
      overlay.className = "fixed inset-0 z-[80] flex items-end bg-black/45 p-3 sm:items-center sm:justify-center";
      var body = fields.map(function (field) {
        var key = V.esc(field.key);
        var label = '<label class="mb-1 block text-xs font-extrabold text-slate-600 dark:text-slate-300">' + V.esc(field.label) + '</label>';
        if (field.type === "checkbox") {
          return '<label class="flex items-center justify-between gap-3 rounded-lg border border-slate-200 px-3 py-2.5 dark:border-slate-700"><span><span class="block text-sm font-bold">' + V.esc(field.label) + '</span>' +
            (field.help ? '<span class="block text-xs text-slate-500 dark:text-slate-400">' + V.esc(field.help) + '</span>' : '') +
            '</span><input data-field="' + key + '" type="checkbox" class="h-5 w-5" ' + (field.value ? 'checked' : '') + '></label>';
        }
        if (field.type === "select") {
          return '<div>' + label + '<select data-field="' + key + '" class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm dark:border-slate-700 dark:bg-[#151a20]">' +
            (field.options || []).map(function (option) {
              return '<option value="' + V.esc(option.value) + '" ' + (String(option.value) === String(field.value) ? 'selected' : '') + '>' + V.esc(option.label) + '</option>';
            }).join("") + '</select></div>';
        }
        if (field.type === "imagePreview") {
          return '<div>' + label + '<div class="flex items-center gap-3 rounded-lg border border-slate-200 p-2.5 dark:border-slate-700">' +
            '<img data-channel-image src="' + V.esc(field.value || "") + '" alt="Current Channel image" class="h-14 w-14 rounded-lg object-cover">' +
            '<div class="text-xs text-slate-500 dark:text-slate-400">Current custom image</div></div></div>';
        }
        if (field.type === "file") {
          return '<div>' + label + '<input data-field="' + key + '" type="file" accept="' + V.esc(field.accept || "") + '" class="block w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm file:mr-3 file:rounded-md file:border-0 file:bg-monita-50 file:px-3 file:py-1.5 file:text-xs file:font-extrabold file:text-monita-700 dark:border-slate-700 dark:bg-[#151a20] dark:file:bg-monita-950 dark:file:text-monita-300">' +
            (field.help ? '<div class="mt-1 text-[11px] text-slate-500 dark:text-slate-400">' + V.esc(field.help) + '</div>' : '') + '</div>';
        }
        var tag = field.type === "textarea" ? "textarea" : "input";
        var type = field.type === "password" ? "password" : field.type === "number" ? "number" : "text";
        var attrs = tag === "input" ? ' type="' + type + '"' : ' rows="3"';
        return '<div>' + label + '<' + tag + ' data-field="' + key + '"' + attrs +
          (field.required ? ' required' : '') + ' value="' + (tag === "input" ? V.esc(field.value || "") : '') + '" placeholder="' + V.esc(field.placeholder || "") + '" class="w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm dark:border-slate-700 dark:bg-[#151a20]">' +
          (tag === "textarea" ? V.esc(field.value || "") : '') + '</' + tag + '>' +
          (field.help ? '<div class="mt-1 text-[11px] text-slate-500 dark:text-slate-400">' + V.esc(field.help) + '</div>' : '') + '</div>';
      }).join("");

      overlay.innerHTML = '<form id="monita-sheet-form" class="max-h-[86vh] w-full max-w-md overflow-y-auto rounded-xl border border-slate-200 bg-white p-4 shadow-2xl dark:border-slate-700 dark:bg-[#151a20]">' +
        '<div class="flex items-center justify-between gap-3"><div class="text-base font-extrabold">' + V.esc(title) + '</div><button type="button" data-dialog-cancel class="rounded-lg px-2 py-1 text-xs font-extrabold text-slate-500">Cancel</button></div>' +
        '<div class="mt-4 space-y-3">' + body + '</div><div data-form-error class="mt-3 hidden rounded-lg bg-red-50 px-3 py-2 text-xs font-bold text-red-700 dark:bg-red-950/40 dark:text-red-300"></div><button type="submit" class="mt-5 w-full rounded-lg bg-monita-700 px-4 py-3 text-sm font-extrabold text-white">' + V.esc(submitLabel || "Save") + '</button></form>';
      document.body.appendChild(overlay);

      function cancel() { closeOverlay(); resolve(null); }
      overlay.querySelector("[data-dialog-cancel]").addEventListener("click", cancel);
      overlay.addEventListener("click", function (event) { if (event.target === overlay) cancel(); });
      overlay.querySelector("#monita-sheet-form").addEventListener("submit", async function (event) {
        event.preventDefault();
        var errorBox = overlay.querySelector("[data-form-error]");
        if (errorBox) {
          errorBox.classList.add("hidden");
          errorBox.textContent = "";
        }
        try {
          var result = {};
          for (var i = 0; i < fields.length; i += 1) {
            var field = fields[i];
            var el = overlay.querySelector('[data-field="' + field.key + '"]');
            if (!el) continue;
            if (field.type === "checkbox") result[field.key] = !!el.checked;
            else if (field.type === "number") result[field.key] = el.value === "" ? null : Number(el.value);
            else if (field.type === "file") result[field.key] = await readImageFile(el.files && el.files[0]);
            else result[field.key] = el.value;
          }
          closeOverlay();
          resolve(result);
        } catch (error) {
          if (errorBox) {
            errorBox.textContent = (error && error.message) || "Could not use the selected file.";
            errorBox.classList.remove("hidden");
          }
        }
      });
    });
  }

  async function showSecuritySettings() {
    var security = state.boot && state.boot.security || {};
    var available = security.availableModes || {};
    var options = [];
    if (available.biometric) options.push({value:"biometric", label:"Biometric only"});
    if (available.credential) options.push({value:"credential", label:"Phone PIN / pattern / password"});
    if (available.either) options.push({value:"either", label:"Biometric or device credential"});

    if (!options.length) {
      showToast("Set up a secure screen lock or biometric authentication in Android first.", "error");
      return;
    }

    var currentMode = security.authMode || "either";
    if (!options.some(function (option) { return option.value === currentMode; })) {
      currentMode = options[0].value;
    }

    var values = await showFormSheet("Security", [
      {
        key:"authMode",
        label:"Authentication method",
        type:"select",
        value:currentMode,
        options:options,
        help:"Monita uses Android's system authentication. Your fingerprint, face data, PIN, pattern, or password is never stored by Monita."
      },
      {
        key:"appLockEnabled",
        label:"Require authentication to open Monita",
        type:"checkbox",
        value:!!security.appLockEnabled,
        help:"When enabled, your existing signed-in session is hidden until Android authenticates you."
      }
    ], "Save security");

    if (!values) return;
    await runAction(async function () {
      var result = await native("configureSecurity", {
        authMode:values.authMode,
        appLockEnabled:values.appLockEnabled
      });
      if (!result || result.cancelled || result.saved === false) return;
      state.boot.security = result;
      render();
      showToast("Security settings updated");
    });
  }

  function showImageViewer(url, label) {
    var safeUrl = V.safeImageUrl(url);
    if (!safeUrl && /^data:image\/(jpeg|png|gif|webp);base64,[A-Za-z0-9+/=]+$/i.test(String(url || ""))) safeUrl = url;
    if (!safeUrl) {
      showToast("This image URL is not allowed.", "error");
      return;
    }

    closeOverlay();
    var overlay = document.createElement("div");
    overlay.id = "monita-overlay";
    overlay.className = "fixed inset-0 z-[90] flex items-center justify-center bg-black/90 p-3";
    overlay.innerHTML =
      '<div class="relative flex max-h-full w-full max-w-5xl flex-col items-center justify-center">' +
      '<button type="button" data-image-close class="absolute right-0 top-0 z-10 rounded-lg bg-black/60 px-3 py-2 text-sm font-extrabold text-white" aria-label="Close image">Close</button>' +
      '<img src="' + V.esc(safeUrl) + '" alt="' + V.esc(label || "Message image") + '" referrerpolicy="no-referrer" class="max-h-[84vh] max-w-full rounded-xl object-contain shadow-2xl">' +
      (label ? '<div class="mt-3 max-w-full truncate rounded-lg bg-black/60 px-3 py-1.5 text-xs font-semibold text-white">' + V.esc(label) + '</div>' : '') +
      '</div>';
    document.body.appendChild(overlay);

    var viewerImage = overlay.querySelector("img");
    viewerImage.addEventListener("error", function () {
      viewerImage.replaceWith(Object.assign(document.createElement("div"), {
        className: "flex min-h-48 w-full items-center justify-center rounded-xl border border-white/20 bg-black/40 px-6 text-center text-sm font-bold text-white",
        textContent: "Image unavailable"
      }));
    });

    var close = function () { closeOverlay(); };
    overlay.querySelector("[data-image-close]").addEventListener("click", close);
    overlay.addEventListener("click", function (event) {
      if (event.target === overlay) close();
    });
  }

  function render() {
    applyTheme();
    if (!state.boot) {
      root.innerHTML = V.loading(state, "Connecting");
      bindEvents();
      return;
    }
    if (state.tab === "inbox") root.innerHTML = V.inbox(state);
    else if (state.tab === "chats") root.innerHTML = V.chats(state);
    else if (state.tab === "channels") root.innerHTML = V.channels(state);
    else if (state.tab === "channel") root.innerHTML = V.channel(state);
    else if (state.tab === "admin") root.innerHTML = V.admin(state);
    else root.innerHTML = V.inbox(state);
    bindEvents();
  }

  async function runAction(fn) {
    if (state.busy) return;
    state.busy = true;
    try {
      await fn();
    } catch (e) {
      if ((e && (e.needsElevation || e.code === 403)) && state.tab === "admin") {
        state.admin = null;
        state.adminNeedsElevation = true;
        render();
      } else {
        showToast((e && e.message) || "Request failed", "error");
      }
    } finally {
      state.busy = false;
    }
  }

  async function refreshBootstrap(showLoading) {
    if (showLoading && !state.boot) root.innerHTML = V.loading(state, "Connecting");
    try {
      state.boot = await native("bootstrap");
      var admin = !!((state.boot.user && state.boot.user.admin) || (state.boot.settingsUser && state.boot.settingsUser.admin));
      if (!admin && state.tab === "admin") state.tab = "inbox";
      if (typingSupported()) native("startMonitaEvents").catch(function () {});
      var requestedChannel = state.boot.openChannelId;
      render();
      if (requestedChannel) await openChannel(Number(requestedChannel), false);
    } catch (e) {
      var msg = V.esc((e && e.message) || "The Monita server is unavailable.");
      root.innerHTML = '<div class="flex min-h-screen items-center justify-center bg-slate-50 p-6 dark:bg-[#0e1216]"><div class="w-full max-w-md rounded-3xl border border-red-200 bg-white p-6 shadow-xl dark:border-red-900 dark:bg-[#151a20]">' +
        '<img src="monita-icon.png" alt="Monita" class="mb-5 h-14 w-14 rounded-2xl object-contain"><h1 class="text-xl font-black">Could not connect</h1><p class="mt-2 text-sm leading-6 text-slate-500 dark:text-slate-400">' + msg + '</p>' +
        '<button id="retry-bootstrap" class="mt-5 w-full rounded-2xl bg-monita-600 px-4 py-3 text-sm font-extrabold text-white">Try again</button></div></div>';
      document.getElementById("retry-bootstrap").addEventListener("click", function () { refreshBootstrap(true); });
    }
  }

  async function refreshCurrent() {
    await refreshBootstrap(false);
    if (state.tab === "channel") {
      await loadChannel(state.selectedChannelId, false);
      return;
    }
    if ((state.tab === "inbox" || state.tab === "chats" || state.tab === "channels") &&
        state.viewModes[state.tab] === "archived") {
      await loadArchive(false);
      return;
    }
    if (state.tab === "admin") await loadAdmin();
  }

  async function switchTab(tab) {
    if (state.tab === "channel") {
      sendTyping(false);
      clearTimeout(typingStopTimer);
      if (state.selectedChannelId) {
        native("lockChannel", {appId:state.selectedChannelId}).catch(function () {});
      }
    }
    state.selectedChannelId = null;
    state.channelMessages = [];
    state.mentionableUsers = [];
    state.channelArchived = false;
    if (tab === "admin") {
      state.tab = "admin";
      render();
      await loadAdmin();
      return;
    }
    state.tab = tab;
    render();
    if (state.viewModes[tab] === "archived") await loadArchive(false);
  }

  async function setViewMode(section, mode) {
    if (section === "channel") {
      state.channelArchived = mode === "archived";
      sendTyping(false);
      clearTimeout(typingStopTimer);
      await loadChannel(state.selectedChannelId, true);
      if (!state.channelArchived) await loadMentionableUsers();
      return;
    }
    state.viewModes[section] = mode;
    render();
    if (mode === "archived") await loadArchive(false);
  }

  async function openChannel(id, archived) {
    var channel = (state.boot.channels || []).find(function (c) { return Number(c.id) === Number(id); });
    if (!channel) return;

    if (V.isProtectedChannel(state, id)) {
      try {
        var access = await native("unlockChannel", {appId:id});
        if (!access || !access.unlocked) return;
      } catch (e) {
        showToast((e && e.message) || "Could not unlock this Channel", "error");
        return;
      }
    } else {
      await native("unlockChannel", {appId:id}).catch(function () {});
    }

    state.channelReturnTab = V.isChat(channel) ? "chats" : "channels";
    state.selectedChannelId = id;
    state.channelArchived = !!archived;
    state.tab = "channel";
    state.channelMessages = [];
    state.mentionableUsers = [];
    state.chatDraft = "";
    state.chatImages = [];
    render();
    await loadChannel(id, true);
    if (!state.channelArchived) await loadMentionableUsers();
    updateMentionSuggestions(document.getElementById("chat-input"));
  }

  function leaveChannel() {
    sendTyping(false);
    clearTimeout(typingStopTimer);
    if (state.selectedChannelId) {
      native("lockChannel", {appId:state.selectedChannelId}).catch(function () {});
    }
    state.tab = state.channelReturnTab || "channels";
    state.selectedChannelId = null;
    state.channelMessages = [];
    state.mentionableUsers = [];
    state.chatDraft = "";
    state.chatImages = [];
    state.attachmentImageCache.clear();
    state.channelArchived = false;
    render();
  }

  async function loadChannel(id, showLoading) {
    var previousY = window.scrollY;
    var previousHeight = document.body.scrollHeight;
    var distanceFromBottom = previousHeight - previousY - window.innerHeight;
    var shouldStickToBottom = !!showLoading || distanceFromBottom < 180;
    if (showLoading) root.innerHTML = V.loading(state, state.channelArchived ? "Loading archive" : "Loading channel");
    try {
      var paged = await native("messages", {appId: id, archived: state.channelArchived, limit: 120});
      state.channelMessages = ((paged && paged.messages) || []).slice().reverse();
      render();
      if (!state.channelArchived) {
        setTimeout(function () {
          if (shouldStickToBottom) {
            window.scrollTo({top: document.body.scrollHeight, behavior: "instant"});
          } else {
            window.scrollTo({top: previousY, behavior: "instant"});
          }
        }, 0);
      }
    } catch (e) {
      showToast((e && e.message) || "Could not load channel", "error");
      render();
    }
  }

  async function loadArchive(showLoading) {
    if (showLoading) root.innerHTML = V.loading(state, "Loading archive");
    try {
      var paged = await native("messages", {archived: true, limit: 200});
      state.archiveMessages = (paged && paged.messages) || [];
      render();
    } catch (e) {
      showToast((e && e.message) || "Could not load archive", "error");
      render();
    }
  }

  async function loadAdmin() {
    try {
      state.admin = await native("adminOverview");
      state.adminNeedsElevation = false;
      render();
    } catch (e) {
      if (e && (e.needsElevation || e.code === 403)) {
        state.admin = null;
        state.adminNeedsElevation = true;
        render();
      } else {
        showToast((e && e.message) || "Could not load admin controls", "error");
      }
    }
  }

  async function refreshAdmin() {
    state.admin = await native("adminOverview");
    state.adminNeedsElevation = false;
    render();
  }

  function findAdminUser(id) {
    return (state.admin && state.admin.users || []).find(function (item) { return Number(item.id) === Number(id); });
  }

  function findAdminGroup(id) {
    return (state.admin && state.admin.groups || []).find(function (item) { return Number(item.id) === Number(id); });
  }

  function findAdminChannel(id) {
    return (state.admin && state.admin.channels || []).find(function (item) { return Number(item.id) === Number(id); });
  }

  async function editUser(user) {
    var values = await showFormSheet(user ? "Edit user" : "Add user", [
      {key:"name", label:"Username", value:user && user.name || "", required:true},
      {key:"displayName", label:"Display name", value:user && user.displayName || ""},
      {key:"password", label:user ? "New password" : "Password", type:"password", required:!user, help:user ? "Leave blank to keep the current password." : ""},
      {key:"admin", label:"Administrator", type:"checkbox", value:!!(user && user.admin), help:"Administrators can unlock the Admin area and perform elevated actions."}
    ], user ? "Save user" : "Create user");
    if (!values) return;
    await runAction(async function () {
      if (user) {
        await native("updateUser", {userId:user.id, name:values.name, displayName:values.displayName, password:values.password || "", admin:values.admin});
      } else {
        await native("createUser", {name:values.name, displayName:values.displayName, password:values.password, admin:values.admin});
      }
      await refreshAdmin();
      showToast(user ? "User updated" : "User created");
    });
  }

  async function editGroup(group) {
    var values = await showFormSheet(group ? "Edit group" : "Add group", [
      {key:"name", label:"Group name", value:group && group.name || "", required:true},
      {key:"description", label:"Description", type:"textarea", value:group && group.description || ""}
    ], group ? "Save group" : "Create group");
    if (!values) return;
    await runAction(async function () {
      if (group) await native("updateGroup", {groupId:group.id, name:values.name, description:values.description});
      else await native("createGroup", {name:values.name, description:values.description});
      await refreshAdmin();
      showToast(group ? "Group updated" : "Group created");
    });
  }

  async function manageGroupMembers(group) {
    var members = await native("groupMembers", {groupId:group.id}) || [];
    var current = new Set(members.map(function (m) { return Number(m.userId); }));
    var candidates = (state.admin.users || []).filter(function (u) { return !current.has(Number(u.id)); });
    closeOverlay();
    var overlay = document.createElement("div");
    overlay.id = "monita-overlay";
    overlay.className = "fixed inset-0 z-[80] flex items-end bg-black/45 p-3 sm:items-center sm:justify-center";
    overlay.innerHTML = '<div class="max-h-[86vh] w-full max-w-md overflow-y-auto rounded-xl border border-slate-200 bg-white p-4 shadow-2xl dark:border-slate-700 dark:bg-[#151a20]">' +
      '<div class="flex items-center justify-between"><div><div class="text-base font-extrabold">' + V.esc(group.name) + '</div><div class="text-xs text-slate-500">Group members</div></div><button data-close class="text-xs font-extrabold text-slate-500">Done</button></div>' +
      '<div class="mt-4 space-y-2">' + (members.length ? members.map(function (m) {
        return '<div class="flex items-center gap-3 rounded-lg border border-slate-200 px-3 py-2.5 dark:border-slate-700"><div class="min-w-0 flex-1"><div class="truncate text-sm font-bold">' + V.esc(m.displayName || m.name) + '</div><div class="text-xs text-slate-400">@' + V.esc(m.name) + '</div></div><button data-remove-user="' + m.userId + '" class="rounded-lg px-2 py-1.5 text-xs font-extrabold text-red-600">Remove</button></div>';
      }).join("") : '<div class="text-sm text-slate-500">No members yet.</div>') + '</div>' +
      (candidates.length ? '<div class="mt-5 border-t border-slate-100 pt-4 dark:border-slate-800"><div class="mb-2 text-xs font-extrabold">Add member</div><div class="flex gap-2"><select data-add-select class="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm dark:border-slate-700 dark:bg-[#151a20]">' +
        candidates.map(function (u) { return '<option value="' + u.id + '">' + V.esc(u.displayName || u.name) + ' (@' + V.esc(u.name) + ')</option>'; }).join("") +
        '</select><button data-add-user class="rounded-lg bg-monita-700 px-3 py-2 text-xs font-extrabold text-white">Add</button></div></div>' : '') + '</div>';
    document.body.appendChild(overlay);
    overlay.querySelector("[data-close]").addEventListener("click", closeOverlay);
    overlay.addEventListener("click", function (e) { if (e.target === overlay) closeOverlay(); });
    var add = overlay.querySelector("[data-add-user]");
    if (add) add.addEventListener("click", async function () {
      var select = overlay.querySelector("[data-add-select]");
      await runAction(async function () {
        await native("addGroupMember", {groupId:group.id, userId:Number(select.value)});
        await refreshAdmin();
        await manageGroupMembers(findAdminGroup(group.id) || group);
      });
    });
    overlay.querySelectorAll("[data-remove-user]").forEach(function (button) {
      button.addEventListener("click", async function () {
        await runAction(async function () {
          await native("removeGroupMember", {groupId:group.id, userId:Number(button.dataset.removeUser)});
          await refreshAdmin();
          await manageGroupMembers(findAdminGroup(group.id) || group);
        });
      });
    });
  }

  function channelImagesSupported() {
    var features = state.boot && state.boot.capabilities && state.boot.capabilities.features;
    return !features || features.channelImages !== false;
  }

  async function editChannel(channel) {
    var chat = channel ? V.isChat(channel) : false;
    var hasCustomImage = !!(channel && channel.image && channel.image !== "static/defaultapp.png");
    var imagesSupported = channelImagesSupported();
    var fields = [
      {key:"name", label:"Name", value:channel && channel.name || "", required:true},
      {key:"description", label:"Description", type:"textarea", value:channel && channel.description || ""},
      {key:"channelType", label:"Type", type:"select", value:chat ? "chat" : "notification", options:[{value:"notification",label:"Notification channel"},{value:"chat",label:"Chat"}]},
      {key:"defaultPriority", label:"Default priority", type:"number", value:channel && channel.defaultPriority != null ? channel.defaultPriority : 0},
      {key:"autoAssign", label:"Global assignment", type:"checkbox", value:!!(channel && channel.autoAssign), help:"Automatically assign this to current and future users."},
      {key:"allowMemberPost", label:"Member posting", type:"checkbox", value:!!(channel && channel.allowMemberPost), help:"Allow ordinary members to post messages."}
    ];
    if (imagesSupported) {
      var imageFields = [];
      if (hasCustomImage) {
        imageFields.push({key:"currentImage", label:"Current Channel image", type:"imagePreview", value:V.channelImageUrl(state, channel)});
      }
      imageFields.push({key:"imageFile", label:channel ? "Change Channel image" : "Channel image", type:"file", accept:"image/png,image/jpeg,image/gif", help:channel ? "Choose a replacement PNG, JPG, JPEG, or GIF up to 5 MB." : "Optional. Choose a PNG, JPG, JPEG, or GIF up to 5 MB."});
      if (hasCustomImage) {
        imageFields.push({key:"removeImage", label:"Remove current Channel image", type:"checkbox", value:false, help:"Restore the default Channel image unless a replacement image is selected above."});
      }
      fields.splice.apply(fields, [4, 0].concat(imageFields));
    }
    var values = await showFormSheet(channel ? "Edit channel" : "Add channel", fields, channel ? "Save channel" : "Create channel");
    if (!values) return;
    if (values.channelType === "chat" && !values.allowMemberPost) values.allowMemberPost = true;
    await runAction(async function () {
      var result;
      if (channel) {
        result = await native("updateChannel", {
          appId:channel.id, name:values.name, description:values.description, defaultPriority:values.defaultPriority,
          channelType:values.channelType, autoAssign:values.autoAssign, allowMemberPost:values.allowMemberPost
        });
        if (!!channel.autoAssign !== !!values.autoAssign) await native("setAutoAssign", {appId:channel.id, enabled:values.autoAssign});
        if (!!channel.allowMemberPost !== !!values.allowMemberPost) await native("setMemberPosting", {appId:channel.id, enabled:values.allowMemberPost});
      } else {
        result = await native("createChannel", {
          name:values.name, description:values.description, defaultPriority:values.defaultPriority,
          channelType:values.channelType, autoAssign:values.autoAssign, allowMemberPost:values.allowMemberPost
        });
      }

      var appId = channel ? channel.id : result && result.id;
      if (appId && values.imageFile) {
        await native("uploadChannelImage", {
          appId:appId,
          filename:values.imageFile.filename,
          mimeType:values.imageFile.mimeType,
          base64:values.imageFile.base64
        });
      } else if (appId && channel && hasCustomImage && values.removeImage) {
        await native("deleteChannelImage", {appId:appId});
      }

      await refreshAdmin();
      await refreshBootstrap(false);
      showToast(channel ? "Channel updated" : "Channel created");
      return result;
    });
  }

  function roleOptions(value) {
    return ["readonly","member","publisher","manager"].map(function (role) {
      return '<option value="' + role + '" ' + (role === value ? 'selected' : '') + '>' + role.charAt(0).toUpperCase() + role.slice(1) + '</option>';
    }).join("");
  }

  async function manageChannelAccess(channel) {
    var results = await Promise.all([
      native("channelMembers", {appId:channel.id}),
      native("assignableChannelUsers", {appId:channel.id}),
      native("assignableChannelGroups", {appId:channel.id}),
      native("channelGroups", {appId:channel.id})
    ]);
    var members = results[0] || [], users = results[1] || [], groups = results[2] || [], assignments = results[3] || [];
    var memberIds = new Set(members.map(function (m) { return Number(m.userId); }));
    var availableUsers = users.filter(function (u) { return !memberIds.has(Number(u.id)); });
    var assignedGroups = new Set(assignments.map(function (g) { return Number(g.groupId); }));
    var availableGroups = groups.filter(function (g) { return !assignedGroups.has(Number(g.id)); });
    var owner = members.find(function (m) { return m.owner; });

    closeOverlay();
    var overlay = document.createElement("div");
    overlay.id = "monita-overlay";
    overlay.className = "fixed inset-0 z-[80] flex items-end bg-black/45 p-3 sm:items-center sm:justify-center";
    var memberRows = members.map(function (m) {
      if (m.owner) {
        return '<div class="rounded-lg border border-monita-200 bg-monita-50 p-3 dark:border-monita-900 dark:bg-monita-950/40"><div class="text-sm font-extrabold">' + V.esc(m.name) + '</div><div class="text-xs text-monita-700 dark:text-monita-300">Owner</div></div>';
      }
      return '<div class="rounded-lg border border-slate-200 p-3 dark:border-slate-700"><div class="mb-2 text-sm font-extrabold">' + V.esc(m.name) + '</div><div class="grid grid-cols-[1fr_auto] gap-2"><select data-member-role="' + m.userId + '" class="rounded-lg border border-slate-300 bg-white px-2 py-2 text-xs dark:border-slate-700 dark:bg-[#151a20]">' + roleOptions(String(m.role || "member").toLowerCase()) + '</select>' +
        '<label class="flex items-center gap-1 text-xs font-bold"><input data-member-notify="' + m.userId + '" type="checkbox" ' + (m.receiveNotifications !== false ? 'checked' : '') + '> Notify</label></div><div class="mt-2 grid grid-cols-2 gap-2"><button data-save-member="' + m.userId + '" class="rounded-lg bg-slate-100 px-2 py-2 text-xs font-extrabold dark:bg-slate-800">Save role</button><button data-delete-member="' + m.userId + '" class="rounded-lg bg-red-50 px-2 py-2 text-xs font-extrabold text-red-700 dark:bg-red-950/40 dark:text-red-300">Remove</button></div></div>';
    }).join("");

    var groupRows = assignments.map(function (g) {
      return '<div class="rounded-lg border border-slate-200 p-3 dark:border-slate-700"><div class="mb-2 text-sm font-extrabold">' + V.esc(g.name || ("Group " + g.groupId)) + '</div><div class="grid grid-cols-[1fr_auto] gap-2"><select data-group-role="' + g.groupId + '" class="rounded-lg border border-slate-300 bg-white px-2 py-2 text-xs dark:border-slate-700 dark:bg-[#151a20]">' + roleOptions(String(g.role || "member").toLowerCase()) + '</select>' +
        '<label class="flex items-center gap-1 text-xs font-bold"><input data-group-notify="' + g.groupId + '" type="checkbox" ' + (g.receiveNotifications !== false ? 'checked' : '') + '> Notify</label></div><div class="mt-2 grid grid-cols-2 gap-2"><button data-save-group="' + g.groupId + '" class="rounded-lg bg-slate-100 px-2 py-2 text-xs font-extrabold dark:bg-slate-800">Save role</button><button data-delete-group="' + g.groupId + '" class="rounded-lg bg-red-50 px-2 py-2 text-xs font-extrabold text-red-700 dark:bg-red-950/40 dark:text-red-300">Remove group</button></div></div>';
    }).join("");

    overlay.innerHTML = '<div class="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-xl border border-slate-200 bg-white p-4 shadow-2xl dark:border-slate-700 dark:bg-[#151a20]">' +
      '<div class="flex items-center justify-between"><div><div class="text-base font-extrabold">' + V.esc(channel.name) + '</div><div class="text-xs text-slate-500">Members, roles and groups</div></div><button data-close class="text-xs font-extrabold text-slate-500">Done</button></div>' +
      '<div class="mt-5"><div class="mb-2 text-xs font-extrabold uppercase tracking-wide text-slate-400">Direct members</div><div class="space-y-2">' + (memberRows || '<div class="text-sm text-slate-500">No direct members.</div>') + '</div>' +
      (availableUsers.length ? '<div class="mt-3 grid grid-cols-1 gap-2 rounded-lg bg-slate-50 p-3 dark:bg-slate-900"><select data-new-user class="rounded-lg border border-slate-300 bg-white px-2 py-2 text-xs dark:border-slate-700 dark:bg-[#151a20]">' + availableUsers.map(function (u) { return '<option value="' + u.id + '">' + V.esc(u.displayName || u.name) + ' (@' + V.esc(u.name) + ')</option>'; }).join("") + '</select><div class="grid grid-cols-2 gap-2"><select data-new-role class="rounded-lg border border-slate-300 bg-white px-2 py-2 text-xs dark:border-slate-700 dark:bg-[#151a20]">' + roleOptions("member") + '</select><button data-add-member class="rounded-lg bg-monita-700 px-2 py-2 text-xs font-extrabold text-white">Add member</button></div></div>' : '') + '</div>' +
      '<div class="mt-5 border-t border-slate-100 pt-4 dark:border-slate-800"><div class="mb-2 text-xs font-extrabold uppercase tracking-wide text-slate-400">Group assignments</div><div class="space-y-2">' + (groupRows || '<div class="text-sm text-slate-500">No group assignments.</div>') + '</div>' +
      (availableGroups.length ? '<div class="mt-3 grid grid-cols-1 gap-2 rounded-lg bg-slate-50 p-3 dark:bg-slate-900"><select data-new-group class="rounded-lg border border-slate-300 bg-white px-2 py-2 text-xs dark:border-slate-700 dark:bg-[#151a20]">' + availableGroups.map(function (g) { return '<option value="' + g.id + '">' + V.esc(g.name) + '</option>'; }).join("") + '</select><div class="grid grid-cols-2 gap-2"><select data-new-group-role class="rounded-lg border border-slate-300 bg-white px-2 py-2 text-xs dark:border-slate-700 dark:bg-[#151a20]">' + roleOptions("member") + '</select><button data-add-group class="rounded-lg bg-monita-700 px-2 py-2 text-xs font-extrabold text-white">Assign group</button></div></div>' : '') + '</div>' +
      (owner && users.length > 1 ? '<div class="mt-5 border-t border-slate-100 pt-4 dark:border-slate-800"><div class="mb-2 text-xs font-extrabold uppercase tracking-wide text-slate-400">Ownership</div><div class="flex gap-2"><select data-owner-select class="min-w-0 flex-1 rounded-lg border border-slate-300 bg-white px-2 py-2 text-xs dark:border-slate-700 dark:bg-[#151a20]">' +
        users.filter(function (u) { return Number(u.id) !== Number(owner.userId); }).map(function (u) { return '<option value="' + u.id + '">' + V.esc(u.displayName || u.name) + '</option>'; }).join("") + '</select><button data-transfer-owner class="rounded-lg bg-amber-100 px-3 py-2 text-xs font-extrabold text-amber-800 dark:bg-amber-950 dark:text-amber-300">Transfer</button></div></div>' : '') + '</div>';
    document.body.appendChild(overlay);
    overlay.querySelector("[data-close]").addEventListener("click", closeOverlay);
    overlay.addEventListener("click", function (e) { if (e.target === overlay) closeOverlay(); });

    var addMember = overlay.querySelector("[data-add-member]");
    if (addMember) addMember.addEventListener("click", async function () {
      await runAction(async function () {
        await native("upsertChannelMember", {appId:channel.id, userId:Number(overlay.querySelector("[data-new-user]").value), role:overlay.querySelector("[data-new-role]").value, receiveNotifications:true});
        await manageChannelAccess(channel);
      });
    });
    overlay.querySelectorAll("[data-save-member]").forEach(function (button) {
      button.addEventListener("click", async function () {
        var uid = Number(button.dataset.saveMember);
        await runAction(async function () {
          await native("upsertChannelMember", {appId:channel.id, userId:uid, role:overlay.querySelector('[data-member-role="' + uid + '"]').value, receiveNotifications:overlay.querySelector('[data-member-notify="' + uid + '"]').checked});
          await manageChannelAccess(channel);
        });
      });
    });
    overlay.querySelectorAll("[data-delete-member]").forEach(function (button) {
      button.addEventListener("click", async function () {
        var uid = Number(button.dataset.deleteMember);
        if (!(await showConfirmDialog("Remove member?", "Remove this user from " + channel.name + "?", "Remove"))) return;
        await runAction(async function () {
          await native("deleteChannelMember", {appId:channel.id, userId:uid});
          await manageChannelAccess(channel);
        });
      });
    });
    var addGroup = overlay.querySelector("[data-add-group]");
    if (addGroup) addGroup.addEventListener("click", async function () {
      await runAction(async function () {
        await native("upsertChannelGroup", {appId:channel.id, groupId:Number(overlay.querySelector("[data-new-group]").value), role:overlay.querySelector("[data-new-group-role]").value, receiveNotifications:true});
        await manageChannelAccess(channel);
      });
    });
    overlay.querySelectorAll("[data-save-group]").forEach(function (button) {
      button.addEventListener("click", async function () {
        var gid = Number(button.dataset.saveGroup);
        await runAction(async function () {
          await native("upsertChannelGroup", {appId:channel.id, groupId:gid, role:overlay.querySelector('[data-group-role="' + gid + '"]').value, receiveNotifications:overlay.querySelector('[data-group-notify="' + gid + '"]').checked});
          await manageChannelAccess(channel);
        });
      });
    });
    overlay.querySelectorAll("[data-delete-group]").forEach(function (button) {
      button.addEventListener("click", async function () {
        var gid = Number(button.dataset.deleteGroup);
        await runAction(async function () {
          await native("deleteChannelGroup", {appId:channel.id, groupId:gid});
          await manageChannelAccess(channel);
        });
      });
    });
    var transfer = overlay.querySelector("[data-transfer-owner]");
    if (transfer) transfer.addEventListener("click", async function () {
      var userId = Number(overlay.querySelector("[data-owner-select]").value);
      if (!(await showConfirmDialog("Transfer ownership?", "The selected user will become the owner of " + channel.name + ".", "Transfer"))) return;
      await runAction(async function () {
        await native("transferChannelOwner", {appId:channel.id, userId:userId});
        await refreshAdmin();
        await refreshBootstrap(false);
        closeOverlay();
        showToast("Ownership transferred");
      });
    });
  }

  function bindEvents() {
    document.querySelectorAll("[data-tab]").forEach(function (el) {
      el.addEventListener("click", function () { switchTab(el.dataset.tab); });
    });

    document.querySelectorAll("[data-view-section]").forEach(function (el) {
      el.addEventListener("click", function () { setViewMode(el.dataset.viewSection, el.dataset.viewMode); });
    });

    document.querySelectorAll("[data-open-channel]").forEach(function (el) {
      el.addEventListener("click", function () {
        openChannel(Number(el.dataset.openChannel), el.dataset.openArchived === "true");
      });
    });

    hydrateSecureMessageImages();

    document.querySelectorAll("[data-secure-image-open]").forEach(function (el) {
      el.addEventListener("click", function (event) {
        event.stopPropagation();
        var image = el.querySelector("img[data-secure-message-image]");
        var url = image && image.dataset.secureImageLoaded === "true" ? image.src : "";
        if (!url) {
          if (image) loadSecureMessageImage(image);
          showToast("Photo is still loading", "normal");
          return;
        }
        showImageViewer(url, el.dataset.imageLabel || "Photo");
      });
    });
    document.querySelectorAll("[data-image-open]").forEach(function (el) {
      el.addEventListener("click", function (event) {
        event.stopPropagation();
        showImageViewer(el.dataset.imageUrl, el.dataset.imageLabel || "Message image");
      });
    });

    document.querySelectorAll("img[data-channel-image]").forEach(function (image) {
      image.addEventListener("error", function () {
        image.classList.add("hidden");
      });
    });

    document.querySelectorAll("img[data-message-image]").forEach(function (image) {
      image.addEventListener("error", function () {
        var holder = image.parentElement;
        if (!holder || holder.querySelector("[data-image-error]")) return;
        image.classList.add("hidden");
        var error = document.createElement("div");
        error.dataset.imageError = "true";
        error.className = "flex min-h-28 w-full items-center justify-center px-4 py-8 text-center text-xs font-extrabold text-slate-500 dark:text-slate-400";
        error.textContent = "Image unavailable";
        holder.appendChild(error);
      });
    });

    document.querySelectorAll("[data-toggle-notifications]").forEach(function (el) {
      el.addEventListener("click", async function (ev) {
        ev.stopPropagation();
        await runAction(async function () {
          var enabled = el.dataset.enabled === "true";
          await native("setNotifications", {appId:Number(el.dataset.toggleNotifications), enabled:enabled});
          await refreshBootstrap(false);
          showToast(enabled ? "Notifications enabled" : "Notifications muted");
        });
      });
    });

    document.querySelectorAll("[data-message-assign]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var messageId = Number(el.dataset.messageAssign);
        var currentUserId = Number(el.dataset.currentUserId);
        var assigned = el.dataset.assigned === "true";
        await runAction(async function () {
          await native("assignMessage", {
            messageId:messageId,
            userId:assigned ? 0 : currentUserId
          });
          if (state.tab === "channel") await loadChannel(state.selectedChannelId, false);
          else await refreshBootstrap(false);
          showToast(assigned ? "Assignment cleared" : "Assigned to you");
        });
      });
    });

    document.querySelectorAll("[data-message-resolve]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var messageId = Number(el.dataset.messageResolve);
        var status = String(el.dataset.status || "open").toLowerCase();
        await runAction(async function () {
          await native("setMessageStatus", {
            messageId:messageId,
            status:status === "resolved" ? "open" : "resolved"
          });
          if (state.tab === "channel") await loadChannel(state.selectedChannelId, false);
          else await refreshBootstrap(false);
          showToast(status === "resolved" ? "Message reopened" : "Message resolved");
        });
      });
    });

    document.querySelectorAll("[data-message-attach]").forEach(function (el) {
      el.addEventListener("click", function () {
        chooseMessageAttachment(Number(el.dataset.messageAttach));
      });
    });

    document.querySelectorAll("[data-archive-message]").forEach(function (el) {
      el.addEventListener("click", async function () {
        await runAction(async function () {
          await native("archiveMessage", {messageId:Number(el.dataset.archiveMessage)});
          if (state.tab === "channel") await loadChannel(state.selectedChannelId, false);
          else await refreshBootstrap(false);
          showToast("Archived");
        });
      });
    });

    document.querySelectorAll("[data-assign-message]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var messageId = Number(el.dataset.assignMessage);
        var assignedToMe = el.dataset.assignedToMe === "true";
        var ownId = state.boot && state.boot.user && Number(state.boot.user.id);
        if (!messageId || !ownId) return;
        await runAction(async function () {
          await native("assignMessage", {messageId:messageId, userId:assignedToMe ? 0 : ownId});
          if (state.tab === "channel") await loadChannel(state.selectedChannelId, false);
          else await refreshBootstrap(false);
          showToast(assignedToMe ? "Assignment cleared" : "Assigned to you");
        });
      });
    });

    document.querySelectorAll("[data-resolve-message]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var messageId = Number(el.dataset.resolveMessage);
        var status = String(el.dataset.status || "open").toLowerCase();
        var next = status === "resolved" ? "open" : "resolved";
        await runAction(async function () {
          await native("setMessageStatus", {messageId:messageId, status:next});
          if (state.tab === "channel") await loadChannel(state.selectedChannelId, false);
          else await refreshBootstrap(false);
          showToast(next === "resolved" ? "Message resolved" : "Message reopened");
        });
      });
    });

    document.querySelectorAll("[data-attach-message]").forEach(function (el) {
      el.addEventListener("click", function () {
        var messageId = Number(el.dataset.attachMessage);
        var input = document.querySelector('[data-message-attach-input="' + messageId + '"]');
        if (input) input.click();
      });
    });

    document.querySelectorAll("[data-message-attach-input]").forEach(function (input) {
      input.addEventListener("change", async function () {
        var messageId = Number(input.dataset.messageAttachInput);
        var file = input.files && input.files[0];
        if (!messageId || !file) return;
        try {
          var payload = await readMessageAttachment(file);
          if (!payload) return;
          await runAction(async function () {
            await native("attachToMessage", {
              messageId:messageId,
              filename:payload.filename,
              mimeType:payload.mimeType,
              base64:payload.base64
            });
            state.attachmentImageCache.clear();
            if (state.tab === "channel") await loadChannel(state.selectedChannelId, false);
            else await refreshBootstrap(false);
            showToast("Attachment added");
          });
        } catch (error) {
          showToast((error && error.message) || "Could not add attachment", "error");
        } finally {
          input.value = "";
        }
      });
    });

    document.querySelectorAll("[data-delete-message]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var messageId = Number(el.dataset.deleteMessage);
        if (!(await showConfirmDialog(
          "Delete message?",
          "Permanently delete this message and its included images/attachments?",
          "Delete"
        ))) return;
        await runAction(async function () {
          await native("deleteMessage", {messageId:messageId});
          state.attachmentImageCache.clear();
          if (state.tab === "channel") {
            await loadChannel(state.selectedChannelId, false);
          } else {
            await refreshBootstrap(false);
            await loadArchive(false);
          }
          showToast("Message deleted");
        });
      });
    });

    document.querySelectorAll("[data-restore-message]").forEach(function (el) {
      el.addEventListener("click", async function () {
        await runAction(async function () {
          await native("restoreMessage", {messageId:Number(el.dataset.restoreMessage)});
          await loadArchive(false);
          if (state.tab === "channel" && state.channelArchived) await loadChannel(state.selectedChannelId, false);
          showToast("Restored");
        });
      });
    });

    var refresh = document.querySelector('[data-action="refresh"]');
    if (refresh) refresh.addEventListener("click", refreshCurrent);
    var securityButton = document.querySelector('[data-action="security"]');
    if (securityButton) securityButton.addEventListener("click", showSecuritySettings);
    var theme = document.querySelector('[data-action="theme"]');
    if (theme) theme.addEventListener("click", cycleTheme);
    var logout = document.querySelector('[data-action="logout"]');
    if (logout) logout.addEventListener("click", async function () {
      if (await showConfirmDialog("Log out?", "You’ll need to sign in again on this device.", "Log out")) await native("logout");
    });
    var back = document.querySelector('[data-action="back-channel-list"]');
    if (back) back.addEventListener("click", leaveChannel);

    var channelProtection = document.querySelector("[data-channel-protection]");
    if (channelProtection) channelProtection.addEventListener("click", async function () {
      var appId = Number(channelProtection.dataset.channelProtection);
      var currentlyProtected = channelProtection.dataset.protected === "true";
      await runAction(async function () {
        var result = await native("setChannelProtected", {
          appId:appId,
          protected:!currentlyProtected
        });
        if (!result || result.cancelled || result.saved === false) return;
        state.boot.security = result;
        render();
        showToast(currentlyProtected ? "Channel protection removed" : "Channel protected on this device");
      });
    });

    var chatInput = document.getElementById("chat-input");
    if (chatInput) {
      chatInput.addEventListener("input", function () {
        state.chatDraft = chatInput.value;
        updateMentionSuggestions(chatInput);
        var hasText = chatInput.value.trim().length > 0;
        clearTimeout(typingStopTimer);
        if (!hasText) { sendTyping(false); return; }
        if (Date.now() - lastTypingSentAt > 2200) {
          lastTypingSentAt = Date.now();
          sendTyping(true);
        }
        typingStopTimer = setTimeout(function () { sendTyping(false); }, 2600);
      });
      chatInput.addEventListener("blur", function () {
        clearTimeout(typingStopTimer);
        sendTyping(false);
        setTimeout(function () {
          var box = document.getElementById("mention-suggestions");
          if (box) box.classList.add("hidden");
        }, 180);
      });
    }

    var addPhoto = document.querySelector("[data-chat-image-add]");
    var imageInput = document.getElementById("chat-image-input");
    if (addPhoto && imageInput) {
      addPhoto.addEventListener("click", function () { imageInput.click(); });
      imageInput.addEventListener("change", function () {
        var files = imageInput.files;
        imageInput.value = "";
        void addChatImages(files);
      });
    }
    document.querySelectorAll("[data-chat-image-remove]").forEach(function (button) {
      button.addEventListener("click", function () {
        removeChatImage(Number(button.dataset.chatImageRemove));
      });
    });

    var chatForm = document.getElementById("chat-form");
    if (chatForm) chatForm.addEventListener("submit", async function (ev) {
      ev.preventDefault();
      var input = document.getElementById("chat-input");
      var text = input && input.value ? input.value.trim() : "";
      if (!text && !state.chatImages.length) return;
      clearTimeout(typingStopTimer);
      sendTyping(false);
      if (input) input.disabled = true;
      var sent = false;
      await runAction(async function () {
        var selected = selectedChannel();
        if (V.chatImagesSupported(state)) {
          await native("sendChatMessage", {
            appId:state.selectedChannelId,
            message:text,
            priority:selected && selected.defaultPriority != null ? Number(selected.defaultPriority) : 0,
            images:state.chatImages.map(function (image) {
              var comma = image.preview.indexOf(",");
              return {filename:image.filename, mimeType:image.mimeType, size:image.size, base64:comma >= 0 ? image.preview.slice(comma + 1) : ""};
            })
          });
        } else {
          await native("sendMessage", {appId:state.selectedChannelId, message:text});
        }
        sent = true;
        state.chatDraft = "";
        state.chatImages = [];
        var mentionBox = document.getElementById("mention-suggestions");
        if (mentionBox) { mentionBox.classList.add("hidden"); mentionBox.innerHTML = ""; }
        await loadChannel(state.selectedChannelId, false);
      });
      if (!sent) {
        render();
        var restored = document.getElementById("chat-input");
        if (restored) restored.focus();
        return;
      }
      var refreshed = document.getElementById("chat-input");
      if (refreshed) refreshed.focus();
    });

    var adminForm = document.getElementById("admin-unlock-form");
    if (adminForm) adminForm.addEventListener("submit", async function (ev) {
      ev.preventDefault();
      var input = document.getElementById("admin-password");
      var password = input && input.value ? input.value : "";
      if (!password) return;
      await runAction(async function () {
        state.admin = await native("adminUnlock", {password:password});
        state.adminNeedsElevation = false;
        state.adminSection = "overview";
        render();
        showToast("Admin controls unlocked");
      });
    });

    document.querySelectorAll("[data-admin-section]").forEach(function (el) {
      el.addEventListener("click", function () {
        state.adminSection = el.dataset.adminSection;
        render();
      });
    });

    var addUser = document.querySelector("[data-admin-user-add]");
    if (addUser) addUser.addEventListener("click", function () { editUser(null); });
    document.querySelectorAll("[data-admin-user-edit]").forEach(function (el) {
      el.addEventListener("click", function () { editUser(findAdminUser(el.dataset.adminUserEdit)); });
    });
    document.querySelectorAll("[data-admin-user-delete]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var user = findAdminUser(el.dataset.adminUserDelete);
        if (!user || !(await showConfirmDialog("Delete user?", "Delete " + (user.displayName || user.name) + "? This cannot be undone.", "Delete"))) return;
        await runAction(async function () {
          await native("deleteUser", {userId:user.id});
          await refreshAdmin();
          showToast("User deleted");
        });
      });
    });

    var addGroupButton = document.querySelector("[data-admin-group-add]");
    if (addGroupButton) addGroupButton.addEventListener("click", function () { editGroup(null); });
    document.querySelectorAll("[data-admin-group-edit]").forEach(function (el) {
      el.addEventListener("click", function () { editGroup(findAdminGroup(el.dataset.adminGroupEdit)); });
    });
    document.querySelectorAll("[data-admin-group-delete]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var group = findAdminGroup(el.dataset.adminGroupDelete);
        if (!group || !(await showConfirmDialog("Delete group?", "Delete " + group.name + "? Channel assignments to this group will be removed.", "Delete"))) return;
        await runAction(async function () {
          await native("deleteGroup", {groupId:group.id});
          await refreshAdmin();
          showToast("Group deleted");
        });
      });
    });
    document.querySelectorAll("[data-admin-group-members]").forEach(function (el) {
      el.addEventListener("click", function () { manageGroupMembers(findAdminGroup(el.dataset.adminGroupMembers)); });
    });

    var addChannel = document.querySelector("[data-admin-channel-add]");
    if (addChannel) addChannel.addEventListener("click", function () { editChannel(null); });
    document.querySelectorAll("[data-admin-channel-edit]").forEach(function (el) {
      el.addEventListener("click", function () { editChannel(findAdminChannel(el.dataset.adminChannelEdit)); });
    });
    document.querySelectorAll("[data-admin-channel-delete]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var channel = findAdminChannel(el.dataset.adminChannelDelete);
        if (!channel || !(await showConfirmDialog("Delete channel?", "Delete " + channel.name + " and its messages? This cannot be undone.", "Delete"))) return;
        await runAction(async function () {
          await native("deleteChannel", {appId:channel.id});
          await refreshAdmin();
          await refreshBootstrap(false);
          showToast("Channel deleted");
        });
      });
    });
    document.querySelectorAll("[data-admin-channel-manage]").forEach(function (el) {
      el.addEventListener("click", function () { manageChannelAccess(findAdminChannel(el.dataset.adminChannelManage)); });
    });

    document.querySelectorAll("[data-admin-autoassign]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var enabled = el.dataset.enabled === "true";
        var channel = findAdminChannel(el.dataset.adminAutoassign);
        if (!channel) return;
        if (!(await showConfirmDialog(enabled ? "Enable global assignment?" : "Disable global assignment?",
          enabled ? channel.name + " will be assigned to current and future users." : channel.name + " will stop being automatically assigned.", enabled ? "Enable" : "Disable"))) return;
        await runAction(async function () {
          await native("setAutoAssign", {appId:channel.id, enabled:enabled});
          await refreshAdmin();
          await refreshBootstrap(false);
          showToast("Channel updated");
        });
      });
    });

    document.querySelectorAll("[data-admin-posting]").forEach(function (el) {
      el.addEventListener("click", async function () {
        var enabled = el.dataset.enabled === "true";
        var channel = findAdminChannel(el.dataset.adminPosting);
        if (!channel) return;
        await runAction(async function () {
          await native("setMemberPosting", {appId:channel.id, enabled:enabled});
          await refreshAdmin();
          await refreshBootstrap(false);
          showToast(enabled ? "Member posting enabled" : "Member posting disabled");
        });
      });
    });
  }

  async function autoSyncNow() {
    var active = document.activeElement;
    var editing =
      active &&
      (active.tagName === "INPUT" || active.tagName === "TEXTAREA" || active.tagName === "SELECT");
    if (
      autoSyncRunning ||
      state.busy ||
      editing ||
      !state.boot ||
      document.visibilityState !== "visible"
    ) return;
    autoSyncRunning = true;
    try {
      await refreshCurrent();
    } catch (_) {
      // Keep the last rendered state; the next foreground sync will retry.
    } finally {
      autoSyncRunning = false;
    }
  }

  function startAutoSync() {
    if (autoSyncTimer) clearInterval(autoSyncTimer);
    autoSyncTimer = setInterval(function () { void autoSyncNow(); }, AUTO_SYNC_MS);
  }

  document.addEventListener("visibilitychange", function () {
    if (document.visibilityState === "visible") void autoSyncNow();
  });
  window.addEventListener("focus", function () { void autoSyncNow(); });
  startAutoSync();

  var darkMedia = matchMedia("(prefers-color-scheme: dark)");
  if (darkMedia.addEventListener) {
    darkMedia.addEventListener("change", function () {
      if (state.theme === "system") { applyTheme(); render(); }
    });
  }

  applyTheme();
  render();
  refreshBootstrap(true);
})();