if (typeof Promise !== "undefined" && !Promise.prototype.finally) {
  Promise.prototype.finally = function(callback) {
    const promise = this.constructor;
    return this.then(
      (value) => promise.resolve(callback()).then(() => value),
      (reason) => promise.resolve(callback()).then(() => {
        throw reason;
      })
    );
  };
}
;
if (typeof uni !== "undefined" && uni && uni.requireGlobal) {
  const global2 = uni.requireGlobal();
  ArrayBuffer = global2.ArrayBuffer;
  Int8Array = global2.Int8Array;
  Uint8Array = global2.Uint8Array;
  Uint8ClampedArray = global2.Uint8ClampedArray;
  Int16Array = global2.Int16Array;
  Uint16Array = global2.Uint16Array;
  Int32Array = global2.Int32Array;
  Uint32Array = global2.Uint32Array;
  Float32Array = global2.Float32Array;
  Float64Array = global2.Float64Array;
  BigInt64Array = global2.BigInt64Array;
  BigUint64Array = global2.BigUint64Array;
}
;
if (uni.restoreGlobal) {
  uni.restoreGlobal(Vue, weex, plus, setTimeout, clearTimeout, setInterval, clearInterval);
}
(function(vue) {
  "use strict";
  const _sfc_main$k = /* @__PURE__ */ vue.defineComponent({
    __name: "AuroraBg",
    props: {
      dense: { type: Boolean, required: false, default: false }
    },
    setup(__props, { expose: __expose }) {
      __expose();
      const props = __props;
      const __returned__ = { props };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  const _export_sfc = (sfc, props) => {
    const target = sfc.__vccOpts || sfc;
    for (const [key, val] of props) {
      target[key] = val;
    }
    return target;
  };
  function _sfc_render$j(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: vue.normalizeClass(["aurora", { "aurora-dense": $setup.props.dense }])
      },
      [
        vue.createElementVNode("view", { class: "orb orb-a" }),
        vue.createElementVNode("view", { class: "orb orb-b" }),
        vue.createElementVNode("view", { class: "orb orb-c" })
      ],
      2
      /* CLASS */
    );
  }
  const __easycom_0 = /* @__PURE__ */ _export_sfc(_sfc_main$k, [["render", _sfc_render$j], ["__scopeId", "data-v-c07bf2e2"], ["__file", "D:/ai_project/queqiao-workbench/src/components/AuroraBg.vue"]]);
  const ON_SHOW = "onShow";
  const ON_HIDE = "onHide";
  const ON_LAUNCH = "onLaunch";
  const ON_LOAD = "onLoad";
  const ON_REACH_BOTTOM = "onReachBottom";
  const ON_PULL_DOWN_REFRESH = "onPullDownRefresh";
  function formatAppLog(type, filename, ...args) {
    if (uni.__log__) {
      uni.__log__(type, filename, ...args);
    } else {
      console[type].apply(console, [...args, filename]);
    }
  }
  function resolveEasycom(component, easycom) {
    return typeof component === "string" ? easycom : component;
  }
  const createLifeCycleHook = (lifecycle, flag = 0) => (hook, target = vue.getCurrentInstance()) => {
    if (vue.isInSSRComponentSetup)
      return;
    vue.injectHook(lifecycle, hook, target);
  };
  const onShow = /* @__PURE__ */ createLifeCycleHook(
    ON_SHOW,
    2
    /* HookFlags.PAGE */
  );
  const onHide = /* @__PURE__ */ createLifeCycleHook(
    ON_HIDE,
    2
    /* HookFlags.PAGE */
  );
  const onLaunch = /* @__PURE__ */ createLifeCycleHook(
    ON_LAUNCH,
    1
    /* HookFlags.APP */
  );
  const onLoad = /* @__PURE__ */ createLifeCycleHook(
    ON_LOAD,
    2
    /* HookFlags.PAGE */
  );
  const onReachBottom = /* @__PURE__ */ createLifeCycleHook(
    ON_REACH_BOTTOM,
    2
    /* HookFlags.PAGE */
  );
  const onPullDownRefresh = /* @__PURE__ */ createLifeCycleHook(
    ON_PULL_DOWN_REFRESH,
    2
    /* HookFlags.PAGE */
  );
  var define_import_meta_env_default = {};
  const BASE_URL = define_import_meta_env_default.VITE_API_BASE || "http://localhost:8080";
  const TOKEN_KEY = "queqiao_token";
  const USER_KEY = "queqiao_user";
  function maskPhone(phone) {
    if (!phone)
      return "";
    const p = String(phone).replace(/\D/g, "");
    if (p.length < 7)
      return p;
    return `${p.slice(0, 3)}****${p.slice(-4)}`;
  }
  function getToken() {
    return uni.getStorageSync(TOKEN_KEY) || "";
  }
  function setToken(token) {
    uni.setStorageSync(TOKEN_KEY, token);
  }
  function clearAuth() {
    uni.removeStorageSync(TOKEN_KEY);
    uni.removeStorageSync(USER_KEY);
  }
  function getStoredUser() {
    const u = uni.getStorageSync(USER_KEY);
    return u && u.id ? u : null;
  }
  function setStoredUser(u) {
    uni.setStorageSync(USER_KEY, u);
  }
  let redirecting = false;
  function gotoLogin() {
    if (redirecting)
      return;
    redirecting = true;
    uni.removeStorageSync(TOKEN_KEY);
    uni.reLaunch({ url: "/pages/login/index" });
    setTimeout(() => redirecting = false, 1200);
  }
  function makeAbortError() {
    return { code: -999, message: "请求已取消" };
  }
  const TIMEOUT_MS = 15e3;
  const DEFAULT_RETRY = 1;
  function request(options) {
    const maxRetry = options.retry ?? DEFAULT_RETRY;
    let requestTask = null;
    let aborted = false;
    const externalSignal = options.signal;
    const token = getToken();
    const p = new Promise((resolve, reject) => {
      const run = (attempt) => {
        if (aborted) {
          reject(makeAbortError());
          return;
        }
        requestTask = uni.request({
          url: BASE_URL + options.url,
          method: options.method || "GET",
          data: options.data || {},
          timeout: TIMEOUT_MS,
          header: {
            "Content-Type": "application/json",
            ...token ? { Authorization: `Bearer ${token}` } : {}
          },
          success: (res) => {
            if (aborted)
              return;
            if (res.statusCode >= 200 && res.statusCode < 300) {
              resolve(res.data);
            } else if (res.statusCode === 401) {
              gotoLogin();
              reject({ code: 401, message: "登录已过期，请重新登录" });
            } else {
              const body = res.data;
              reject({
                code: res.statusCode,
                message: body && (body.error || body.message) || `请求失败(${res.statusCode})`
              });
            }
          },
          fail: (err) => {
            if (aborted) {
              reject(makeAbortError());
              return;
            }
            const msg = err && err.errMsg ? String(err.errMsg) : "";
            if (msg.includes("abort")) {
              reject(makeAbortError());
              return;
            }
            if (attempt < maxRetry) {
              run(attempt + 1);
              return;
            }
            reject({ code: -1, message: "网络异常，请检查后端服务是否启动" });
          }
        });
      };
      run(0);
    });
    p.abort = () => {
      aborted = true;
      try {
        requestTask == null ? void 0 : requestTask.abort();
      } catch {
      }
    };
    if (externalSignal) {
      if (externalSignal.aborted)
        p.abort();
      else
        externalSignal.addEventListener("abort", () => p.abort());
    }
    return p;
  }
  const api = {
    // —— 认证 ——
    login: (username, password) => request({
      url: "/api/auth/login",
      method: "POST",
      data: { username, password }
    }),
    me: () => request({ url: "/api/auth/me" }),
    logout: () => request({ url: "/api/auth/logout", method: "POST" }),
    updateProfile: (data) => request({ url: "/api/auth/profile", method: "PUT", data }),
    changePassword: (data) => request({ url: "/api/auth/change-password", method: "POST", data }),
    // —— 工作台 ——
    dashboard: () => request({ url: "/api/dashboard" }),
    // —— 会员 ——
    members: (params) => request({ url: "/api/members", data: params }),
    memberDetail: (id) => request({ url: `/api/members/${id}` }),
    createMember: (data) => request({ url: "/api/members", method: "POST", data }),
    updateMember: (id, data) => request({ url: `/api/members/${id}`, method: "PUT", data }),
    memberStats: () => request({ url: "/api/members/stats" }),
    aiParse: (text) => request({ url: "/api/members/parse", method: "POST", data: { text } }),
    memberFollowups: (id) => request({ url: `/api/members/${id}/followups` }),
    createFollowup: (id, data) => request({ url: `/api/members/${id}/followups`, method: "POST", data }),
    followupSummary: (id) => request({ url: `/api/members/${id}/followups/summary` }),
    // —— 牵线 ——
    pairs: (params) => request({ url: "/api/pairs", data: params }),
    pairDetail: (id) => request({ url: `/api/pairs/${id}` }),
    createPair: (data) => request({ url: "/api/pairs", method: "POST", data }),
    pairStats: () => request({ url: "/api/pairs/stats" }),
    pairFeedback: (id, data) => request({ url: `/api/pairs/${id}/feedback`, method: "POST", data }),
    pairMeeting: (id, data) => request({ url: `/api/pairs/${id}/meeting`, method: "POST", data }),
    recommendations: (memberId, limit = 6) => request({
      url: "/api/match/recommendations",
      data: { memberId, limit }
    }),
    // 看板状态流转：更新某条牵线记录状态（跟进/约见/完成/失败）
    updatePairStatus: (id, status, note) => request({ url: `/api/pairs/${id}/status`, method: "PUT", data: { status, note } }),
    // 推荐喜欢（标记候选为喜欢；后端若无该接口则按遗留处理）
    likeRecommendation: (targetId, candidateId) => request({
      url: `/api/match/recommendations/${candidateId}/like`,
      method: "POST",
      data: { targetId }
    }),
    matchScore: (maleId, femaleId) => request({
      url: "/api/match/score",
      data: { maleId, femaleId }
    }),
    // —— 日程 ——
    calendarEvents: (params) => request({ url: "/api/calendar/events", data: params }),
    calendarToday: () => request({ url: "/api/calendar/today" }),
    createCalendarEvent: (data) => request({ url: "/api/calendar/events", method: "POST", data }),
    // —— 消息 ——
    conversations: (kind) => request({ url: "/api/conversations", data: kind ? { kind } : {} }),
    conversationUnread: () => request({ url: "/api/conversations/unread" }),
    conversationDetail: (id) => request({ url: `/api/conversations/${id}` }),
    createConversation: (memberId) => request({ url: "/api/conversations", method: "POST", data: { memberId } }),
    sendMessage: (id, data) => request({
      url: `/api/conversations/${id}/messages`,
      method: "POST",
      data
    }),
    replySuggest: (id) => request({ url: `/api/conversations/${id}/reply-suggest`, method: "POST" })
  };
  function errMsg(e) {
    return e && e.message || "操作失败";
  }
  const _sfc_main$j = /* @__PURE__ */ vue.defineComponent({
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const username = vue.ref("");
      const password = vue.ref("");
      const loading = vue.ref(false);
      const error = vue.ref("");
      const showPwd = vue.ref(false);
      const ROLES = {
        ADMIN: "管理员",
        AGENT: "红娘",
        MANAGER: "主管"
      };
      async function doLogin() {
        if (loading.value)
          return;
        error.value = "";
        if (!username.value.trim() || !password.value) {
          error.value = "请输入账号和密码";
          return;
        }
        loading.value = true;
        try {
          const r = await api.login(username.value.trim(), password.value);
          setToken(r.token);
          setStoredUser(r.user);
          uni.showToast({ title: `欢迎回来，${r.user.name}`, icon: "none" });
          setTimeout(() => uni.reLaunch({ url: "/pages/workspace/index" }), 400);
        } catch (e) {
          error.value = errMsg(e);
        } finally {
          loading.value = false;
        }
      }
      function fill(name) {
        username.value = name;
        password.value = "123456";
      }
      const __returned__ = { username, password, loading, error, showPwd, ROLES, doLogin, fill };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$i(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg, { dense: "" }),
      vue.createElementVNode("view", { class: "brand" }, [
        vue.createElementVNode("view", { class: "logo-wrap anim-float" }, [
          vue.createElementVNode("view", { class: "logo-glow" }),
          vue.createElementVNode("view", { class: "logo" }, "鹊")
        ]),
        vue.createElementVNode("text", { class: "brand-title" }, "鹊桥 · 红娘工作台"),
        vue.createElementVNode("text", { class: "brand-sub" }, "让每一次牵线，都有迹可循")
      ]),
      vue.createElementVNode("view", { class: "form-shell" }, [
        vue.createElementVNode("view", { class: "form-shine" }),
        vue.createElementVNode("view", { class: "form-inner" }, [
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, "账号"),
            vue.withDirectives(vue.createElementVNode(
              "input",
              {
                class: "input",
                "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.username = $event),
                placeholder: "请输入红娘账号",
                "placeholder-class": "ph",
                maxlength: 30
              },
              null,
              512
              /* NEED_PATCH */
            ), [
              [vue.vModelText, $setup.username]
            ])
          ]),
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, "密码"),
            vue.createElementVNode("view", { class: "pwd-row" }, [
              vue.withDirectives(vue.createElementVNode("input", {
                class: "input",
                "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.password = $event),
                type: $setup.showPwd ? "text" : "password",
                placeholder: "请输入密码",
                "placeholder-class": "ph",
                maxlength: 30,
                "confirm-type": "done",
                onConfirm: $setup.doLogin
              }, null, 40, ["type"]), [
                [vue.vModelDynamic, $setup.password]
              ]),
              vue.createElementVNode(
                "text",
                {
                  class: "toggle",
                  onClick: _cache[2] || (_cache[2] = ($event) => $setup.showPwd = !$setup.showPwd)
                },
                vue.toDisplayString($setup.showPwd ? "隐藏" : "显示"),
                1
                /* TEXT */
              )
            ])
          ]),
          $setup.error ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 0,
              class: "error"
            },
            vue.toDisplayString($setup.error),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true),
          vue.createElementVNode("button", {
            class: "btn",
            disabled: $setup.loading,
            onClick: $setup.doLogin
          }, vue.toDisplayString($setup.loading ? "登录中…" : "登 录"), 9, ["disabled"]),
          vue.createElementVNode("view", { class: "demo" }, [
            vue.createElementVNode("text", { class: "demo-title" }, "演示账号"),
            vue.createElementVNode("view", { class: "demo-row" }, [
              vue.createElementVNode("text", { class: "demo-tag" }, "管理员"),
              vue.createElementVNode("text", {
                class: "demo-name",
                onClick: _cache[3] || (_cache[3] = ($event) => $setup.fill("admin"))
              }, "admin"),
              vue.createElementVNode("text", { class: "demo-tag" }, "红娘"),
              vue.createElementVNode("text", {
                class: "demo-name",
                onClick: _cache[4] || (_cache[4] = ($event) => $setup.fill("wangjie"))
              }, "wangjie")
            ]),
            vue.createElementVNode("text", { class: "demo-tip" }, "点击账号自动填入（密码 123456）")
          ])
        ])
      ])
    ]);
  }
  const PagesLoginIndex = /* @__PURE__ */ _export_sfc(_sfc_main$j, [["render", _sfc_render$i], ["__scopeId", "data-v-45258083"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/login/index.vue"]]);
  const _sfc_main$i = /* @__PURE__ */ vue.defineComponent({
    __name: "LoadingRedLine",
    props: {
      top: { type: String, required: false, default: "0" }
    },
    setup(__props, { expose: __expose }) {
      __expose();
      const __returned__ = {};
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$h(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: "red-line-track",
        style: vue.normalizeStyle({ top: $props.top })
      },
      [
        vue.createElementVNode("view", { class: "red-line" })
      ],
      4
      /* STYLE */
    );
  }
  const __easycom_1 = /* @__PURE__ */ _export_sfc(_sfc_main$i, [["render", _sfc_render$h], ["__scopeId", "data-v-49219f6c"], ["__file", "D:/ai_project/queqiao-workbench/src/components/LoadingRedLine.vue"]]);
  const _sfc_main$h = /* @__PURE__ */ vue.defineComponent({
    __name: "AppIcon",
    props: {
      name: { type: String, required: true },
      size: { type: String, required: false, default: "40rpx" }
    },
    setup(__props, { expose: __expose }) {
      __expose();
      const __returned__ = {};
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$g(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("image", {
      src: `/static/icons/${$props.name}.png`,
      style: vue.normalizeStyle({ width: $props.size, height: $props.size }),
      mode: "aspectFit",
      class: "app-icon"
    }, null, 12, ["src"]);
  }
  const __easycom_2$1 = /* @__PURE__ */ _export_sfc(_sfc_main$h, [["render", _sfc_render$g], ["__scopeId", "data-v-54b59b04"], ["__file", "D:/ai_project/queqiao-workbench/src/components/AppIcon.vue"]]);
  const _sfc_main$g = /* @__PURE__ */ vue.defineComponent({
    __name: "GlassCard",
    props: {
      padding: { type: String, required: false, default: "32rpx" },
      shadow: { type: String, required: false, default: "normal" },
      strong: { type: Boolean, required: false, default: false },
      radius: { type: String, required: false, default: "64rpx" }
    },
    setup(__props, { expose: __expose }) {
      __expose();
      const props = __props;
      const shadowMap = {
        soft: "var(--q-glass-shadow-soft)",
        normal: "var(--q-glass-shadow)",
        strong: "var(--q-glass-shadow-strong)",
        none: "none"
      };
      const __returned__ = { props, shadowMap };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$f(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: "glass-card-wrap",
        style: vue.normalizeStyle({
          padding: $setup.props.padding,
          borderRadius: $setup.props.radius,
          boxShadow: $setup.shadowMap[$setup.props.shadow],
          background: $setup.props.strong ? "var(--q-glass-bg-strong)" : "var(--q-glass-bg)"
        })
      },
      [
        vue.renderSlot(_ctx.$slots, "default", {}, void 0, true)
      ],
      4
      /* STYLE */
    );
  }
  const __easycom_3 = /* @__PURE__ */ _export_sfc(_sfc_main$g, [["render", _sfc_render$f], ["__scopeId", "data-v-9732af8f"], ["__file", "D:/ai_project/queqiao-workbench/src/components/GlassCard.vue"]]);
  const _sfc_main$f = /* @__PURE__ */ vue.defineComponent({
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const me = getStoredUser();
      const matchmakerName = vue.ref((me == null ? void 0 : me.name) || "红娘");
      const roleLabel = (me == null ? void 0 : me.role) === "ADMIN" ? "管理员" : (me == null ? void 0 : me.role) === "MANAGER" ? "主管" : "金牌红娘";
      const hour = (/* @__PURE__ */ new Date()).getHours();
      const greeting = hour < 6 ? "夜深了" : hour < 12 ? "上午好" : hour < 18 ? "下午好" : "晚上好";
      const ICON_MAP = {
        users: "users-primary",
        heart: "heart-white",
        dollar: "file-gold",
        "file-text": "file-ink"
      };
      const kpiIcon = (i) => ICON_MAP[i] || "stat-match";
      const isHeart = (i) => i === "heart";
      const loading = vue.ref(false);
      const offline = vue.ref(false);
      const data = vue.ref(null);
      const kpis = () => {
        var _a;
        return ((_a = data.value) == null ? void 0 : _a.kpis) ?? [];
      };
      const recs = () => {
        var _a;
        return ((_a = data.value) == null ? void 0 : _a.recs) ?? [];
      };
      const todos = () => {
        var _a;
        return ((_a = data.value) == null ? void 0 : _a.todos) ?? [];
      };
      const members = () => {
        var _a;
        return ((_a = data.value) == null ? void 0 : _a.recentMembers) ?? [];
      };
      const fetchDashboard = async () => {
        var _a;
        loading.value = true;
        try {
          const res = await api.dashboard();
          data.value = res;
          offline.value = false;
        } catch (e) {
          data.value = null;
          offline.value = true;
          uni.showToast({ title: e && e.message || "工作台加载失败", icon: "none" });
        } finally {
          loading.value = false;
          (_a = uni.stopPullDownRefresh) == null ? void 0 : _a.call(uni);
        }
      };
      const onRetry = () => {
        offline.value = false;
        fetchDashboard();
      };
      onShow(fetchDashboard);
      onPullDownRefresh(fetchDashboard);
      const tagList = (tags) => tags ? tags.split(/[,，、]/).filter(Boolean) : [];
      const ageText = (m) => `${m.age ?? "-"}岁 · ${m.job || m.city || ""}`;
      const onBell = () => uni.switchTab({ url: "/pages/message/index" });
      const onAiCapsule = () => uni.navigateTo({ url: "/pages/member/input" });
      const onMemberTap = (m) => uni.navigateTo({ url: `/pages/member/detail?id=${m.id}` });
      const onFollow = (m) => uni.navigateTo({ url: `/pages/member/detail?id=${m.id}` });
      const onRecTap = (r) => uni.navigateTo({ url: `/pages/match/index?memberId=${r.forMemberId}&from=rec` });
      const onTodoTap = (t) => {
        const link = t.link || "";
        const pageMap = {
          "/calendar": "/pages/schedule/index",
          "/match": "/pages/match/index",
          "/members": "/pages/members/index",
          "/crm": "/pages/members/index",
          "/audit": "/pages/members/index"
        };
        const target = pageMap[link];
        if (target === "/pages/match/index" || target === "/pages/members/index") {
          uni.switchTab({ url: target });
        } else if (target) {
          uni.navigateTo({ url: target });
        }
      };
      const __returned__ = { statusBarHeight, me, matchmakerName, roleLabel, hour, greeting, ICON_MAP, kpiIcon, isHeart, loading, offline, data, kpis, recs, todos, members, fetchDashboard, onRetry, tagList, ageText, onBell, onAiCapsule, onMemberTap, onFollow, onRecTap, onTodoTap };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$e(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_loading_red_line = resolveEasycom(vue.resolveDynamicComponent("loading-red-line"), __easycom_1);
    const _component_app_icon = resolveEasycom(vue.resolveDynamicComponent("app-icon"), __easycom_2$1);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      $setup.loading ? (vue.openBlock(), vue.createBlock(_component_loading_red_line, {
        key: 0,
        top: $setup.statusBarHeight + "px"
      }, null, 8, ["top"])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      $setup.offline ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "offline-bar flex items-center",
        "hover-class": "hover-opacity",
        onClick: $setup.onRetry
      }, [
        vue.createVNode(_component_app_icon, {
          name: "alert-warning",
          size: "28rpx"
        }),
        vue.createElementVNode("text", { class: "offline-text" }, "数据离线 · 点击重试")
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("view", { class: "glass-bar greeting-bar" }, [
        vue.createElementVNode("view", {
          class: "flex",
          style: { "flex-direction": "column", "gap": "4rpx" }
        }, [
          vue.createElementVNode(
            "text",
            { class: "greeting-text" },
            vue.toDisplayString($setup.greeting) + "，" + vue.toDisplayString($setup.matchmakerName),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "greeting-role" },
            vue.toDisplayString($setup.roleLabel),
            1
            /* TEXT */
          )
        ]),
        vue.createElementVNode("view", {
          class: "bell-btn flex-center",
          "hover-class": "hover-scale",
          onClick: $setup.onBell
        }, [
          vue.createVNode(_component_app_icon, {
            name: "bell",
            size: "40rpx"
          }),
          vue.createElementVNode("view", { class: "bell-dot" })
        ])
      ]),
      vue.createElementVNode("view", {
        class: "ai-capsule",
        "hover-class": "hover-lift",
        onClick: $setup.onAiCapsule
      }, [
        vue.createElementVNode("view", { class: "ai-glow anim-breathe" }),
        vue.createElementVNode("view", { class: "ai-inner" }, [
          vue.createElementVNode("view", { class: "mic-disc flex-center" }, [
            vue.createElementVNode("view", { class: "mic-pulse" }),
            vue.createVNode(_component_app_icon, {
              name: "mic",
              size: "48rpx"
            })
          ]),
          vue.createElementVNode("text", { class: "ai-title" }, "粘贴文本，AI 自动建档"),
          vue.createElementVNode("text", { class: "ai-sub" }, "自然语言一键录入新会员 · 支持语音转写")
        ])
      ]),
      $setup.kpis().length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "section"
      }, [
        vue.createElementVNode("view", { class: "section-title section-head" }, [
          vue.createElementVNode("text", { class: "section-name" }, "数据概览")
        ]),
        vue.createElementVNode("view", { class: "bento board-bento" }, [
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.kpis(), (k, i) => {
              return vue.openBlock(), vue.createElementBlock(
                "view",
                {
                  key: k.label,
                  class: vue.normalizeClass(["bento-cell board-cell", { "cell-hero": i === 0 }]),
                  "hover-class": "hover-lift-card"
                },
                [
                  vue.createElementVNode("view", { class: "board-icon" }, [
                    vue.createVNode(_component_app_icon, {
                      name: $setup.kpiIcon(k.icon),
                      size: "36rpx"
                    }, null, 8, ["name"])
                  ]),
                  vue.createElementVNode(
                    "text",
                    { class: "board-label" },
                    vue.toDisplayString(k.label),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("view", {
                    class: "flex items-baseline",
                    style: { "gap": "8rpx" }
                  }, [
                    vue.createElementVNode(
                      "text",
                      {
                        class: vue.normalizeClass(["board-value", { "display-hero": i === 0 }])
                      },
                      vue.toDisplayString(k.value),
                      3
                      /* TEXT, CLASS */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "board-unit" },
                      vue.toDisplayString(k.unit),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", {
                    class: "flex items-center",
                    style: { "gap": "8rpx", "margin-top": "14rpx" }
                  }, [
                    $setup.isHeart(k.icon) ? (vue.openBlock(), vue.createElementBlock("view", {
                      key: 0,
                      class: "heart-mini flex-center anim-heartbeat"
                    }, [
                      vue.createVNode(_component_app_icon, {
                        name: "heart-white",
                        size: "18rpx"
                      })
                    ])) : vue.createCommentVNode("v-if", true),
                    vue.createElementVNode(
                      "text",
                      {
                        class: vue.normalizeClass(["board-trend", { good: k.up, bad: !k.up }])
                      },
                      vue.toDisplayString(k.trend),
                      3
                      /* TEXT, CLASS */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "cell-shine" })
                ],
                2
                /* CLASS */
              );
            }),
            128
            /* KEYED_FRAGMENT */
          ))
        ])
      ])) : vue.createCommentVNode("v-if", true),
      $setup.recs().length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 3,
        class: "section"
      }, [
        vue.createElementVNode("view", { class: "section-title section-head" }, [
          vue.createElementVNode("text", { class: "section-name" }, "AI 最优匹配推荐")
        ]),
        vue.createElementVNode("view", { class: "rec-list" }, [
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.recs(), (r) => {
              return vue.openBlock(), vue.createBlock(_component_glass_card, {
                key: r.memberId,
                strong: "",
                padding: "32rpx 36rpx",
                shadow: "normal",
                class: "rec-card",
                onClick: ($event) => $setup.onRecTap(r)
              }, {
                default: vue.withCtx(() => [
                  vue.createElementVNode("view", { class: "flex items-center" }, [
                    vue.createElementVNode(
                      "view",
                      {
                        class: "rec-avatar flex-center",
                        style: vue.normalizeStyle({ background: r.avatarBg })
                      },
                      vue.toDisplayString(r.initial),
                      5
                      /* TEXT, STYLE */
                    ),
                    vue.createElementVNode("view", { class: "member-info" }, [
                      vue.createElementVNode("view", {
                        class: "flex items-center",
                        style: { "gap": "12rpx", "margin-bottom": "8rpx" }
                      }, [
                        vue.createElementVNode(
                          "text",
                          { class: "member-name" },
                          vue.toDisplayString(r.name),
                          1
                          /* TEXT */
                        ),
                        vue.createElementVNode("view", { class: "score-pill flex-center" }, [
                          vue.createElementVNode(
                            "text",
                            { class: "score-text" },
                            vue.toDisplayString(r.score) + "分",
                            1
                            /* TEXT */
                          )
                        ])
                      ]),
                      vue.createElementVNode(
                        "text",
                        { class: "rec-detail" },
                        vue.toDisplayString(r.detail),
                        1
                        /* TEXT */
                      ),
                      vue.createElementVNode(
                        "text",
                        { class: "rec-reason" },
                        vue.toDisplayString(r.reason),
                        1
                        /* TEXT */
                      )
                    ]),
                    vue.createVNode(_component_app_icon, {
                      name: "chevron-right",
                      size: "28rpx"
                    })
                  ])
                ]),
                _: 2
                /* DYNAMIC */
              }, 1032, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          ))
        ])
      ])) : vue.createCommentVNode("v-if", true),
      $setup.todos().length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 4,
        class: "section"
      }, [
        vue.createElementVNode("view", { class: "section-title section-head" }, [
          vue.createElementVNode("text", { class: "section-name" }, "今日待办")
        ]),
        vue.createElementVNode("view", { class: "todo-list" }, [
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.todos(), (t, i) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: i,
                class: "todo-row",
                "hover-class": "hover-opacity",
                onClick: ($event) => $setup.onTodoTap(t)
              }, [
                vue.createElementVNode(
                  "view",
                  {
                    class: vue.normalizeClass(["todo-dot", t.priority])
                  },
                  null,
                  2
                  /* CLASS */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "todo-text" },
                  vue.toDisplayString(t.text),
                  1
                  /* TEXT */
                ),
                vue.createVNode(_component_app_icon, {
                  name: "chevron-right",
                  size: "26rpx"
                })
              ], 8, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          ))
        ])
      ])) : vue.createCommentVNode("v-if", true),
      $setup.members().length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 5,
        class: "section"
      }, [
        vue.createElementVNode("view", { class: "flex-between section-head" }, [
          vue.createElementVNode("view", { class: "section-title" }, [
            vue.createElementVNode("text", { class: "section-name" }, "最新入会会员")
          ]),
          vue.createElementVNode("view", {
            class: "flex items-center",
            "hover-class": "hover-opacity",
            onClick: _cache[0] || (_cache[0] = ($event) => uni.switchTab({ url: "/pages/members/index" }))
          }, [
            vue.createElementVNode("text", { class: "more-text" }, "查看全部"),
            vue.createVNode(_component_app_icon, {
              name: "chevron-right",
              size: "28rpx"
            })
          ])
        ]),
        vue.createElementVNode("view", { class: "member-list" }, [
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.members(), (m) => {
              return vue.openBlock(), vue.createElementBlock("view", {
                key: m.id,
                class: "stack-wrap",
                onClick: ($event) => $setup.onMemberTap(m)
              }, [
                vue.createElementVNode("view", { class: "stack-layer" }),
                vue.createVNode(
                  _component_glass_card,
                  {
                    strong: "",
                    padding: "32rpx 36rpx",
                    shadow: "strong",
                    class: "member-card"
                  },
                  {
                    default: vue.withCtx(() => [
                      vue.createElementVNode("view", { class: "flex items-center" }, [
                        vue.createElementVNode("view", { class: "avatar-wrap" }, [
                          vue.createElementVNode(
                            "view",
                            {
                              class: "member-avatar flex-center",
                              style: vue.normalizeStyle({ background: m.avatarBg, color: "#fff" })
                            },
                            vue.toDisplayString(m.name[0]),
                            5
                            /* TEXT, STYLE */
                          ),
                          m.verified ? (vue.openBlock(), vue.createElementBlock("view", {
                            key: 0,
                            class: "badge-shield flex-center"
                          }, [
                            vue.createVNode(_component_app_icon, {
                              name: "shield-white",
                              size: "20rpx"
                            })
                          ])) : vue.createCommentVNode("v-if", true)
                        ]),
                        vue.createElementVNode("view", { class: "member-info" }, [
                          vue.createElementVNode("view", {
                            class: "flex items-baseline",
                            style: { "gap": "12rpx", "margin-bottom": "10rpx" }
                          }, [
                            vue.createElementVNode(
                              "text",
                              { class: "member-name" },
                              vue.toDisplayString(m.name),
                              1
                              /* TEXT */
                            ),
                            vue.createElementVNode(
                              "text",
                              { class: "member-meta" },
                              vue.toDisplayString($setup.ageText(m)),
                              1
                              /* TEXT */
                            )
                          ]),
                          vue.createElementVNode("view", {
                            class: "flex flex-wrap",
                            style: { "gap": "10rpx", "margin-bottom": "12rpx" }
                          }, [
                            (vue.openBlock(true), vue.createElementBlock(
                              vue.Fragment,
                              null,
                              vue.renderList($setup.tagList(m.tags).slice(0, 3), (t) => {
                                return vue.openBlock(), vue.createElementBlock(
                                  "text",
                                  {
                                    key: t,
                                    class: "tag-soft"
                                  },
                                  vue.toDisplayString(t),
                                  1
                                  /* TEXT */
                                );
                              }),
                              128
                              /* KEYED_FRAGMENT */
                            ))
                          ]),
                          vue.createElementVNode("view", {
                            class: "flex items-center",
                            style: { "gap": "8rpx" }
                          }, [
                            vue.createElementVNode(
                              "text",
                              { class: "idle-text" },
                              vue.toDisplayString(m.lastActive || "待跟进"),
                              1
                              /* TEXT */
                            )
                          ])
                        ]),
                        vue.createElementVNode("view", {
                          class: "follow-btn flex-center",
                          "hover-class": "hover-scale",
                          onClick: vue.withModifiers(($event) => $setup.onFollow(m), ["stop"])
                        }, "立即跟进", 8, ["onClick"])
                      ])
                    ]),
                    _: 2
                    /* DYNAMIC */
                  },
                  1024
                  /* DYNAMIC_SLOTS */
                )
              ], 8, ["onClick"]);
            }),
            128
            /* KEYED_FRAGMENT */
          ))
        ])
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("view", { style: { "height": "60rpx" } })
    ]);
  }
  const PagesWorkspaceIndex = /* @__PURE__ */ _export_sfc(_sfc_main$f, [["render", _sfc_render$e], ["__scopeId", "data-v-c2bb628e"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/workspace/index.vue"]]);
  const pageSize = 20;
  const _sfc_main$e = /* @__PURE__ */ vue.defineComponent({
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const keyword = vue.ref("");
      const scope = vue.ref("all");
      const chips = ["女性", "男性", "25-30岁", "本科+", "上海", "有房", "已认证", "高个子"];
      const activeChip = vue.ref("女性");
      const total = vue.ref(0);
      const list = vue.ref([]);
      const loading = vue.ref(false);
      const page = vue.ref(1);
      const finished = vue.ref(false);
      const loadPage = async (reset) => {
        var _a;
        if (reset) {
          page.value = 1;
          finished.value = false;
        }
        loading.value = true;
        try {
          const res = await api.members({
            keyword: keyword.value || void 0,
            gender: activeChip.value === "女性" ? "女" : activeChip.value === "男性" ? "男" : void 0,
            page: page.value,
            size: pageSize
          });
          const items = res.list || [];
          total.value = res.total ?? items.length;
          list.value = reset ? items : [...list.value, ...items];
          if (items.length < pageSize)
            finished.value = true;
        } catch {
          if (reset)
            list.value = [];
          uni.showToast({ title: "后端未连接", icon: "none" });
        } finally {
          loading.value = false;
          (_a = uni.stopPullDownRefresh) == null ? void 0 : _a.call(uni);
        }
      };
      const fetchList = () => loadPage(true);
      onShow(fetchList);
      onReachBottom(() => {
        if (!finished.value && !loading.value) {
          page.value++;
          loadPage(false);
        }
      });
      onPullDownRefresh(fetchList);
      const onSearch = () => fetchList();
      const onChip = (c) => {
        activeChip.value = c;
        fetchList();
      };
      const onView = (m) => uni.navigateTo({ url: `/pages/member/detail?id=${m.id}` });
      const onMatch = (m) => uni.navigateTo({ url: `/pages/match/index?memberId=${m.id}` });
      const tagList = (tags) => tags ? tags.split(/[,，、]/).filter(Boolean) : [];
      const activeNow = (m) => {
        const s2 = m.lastActive || "";
        return s2.includes("刚刚") || s2.includes("分钟");
      };
      const __returned__ = { statusBarHeight, keyword, scope, chips, activeChip, total, list, loading, page, pageSize, finished, loadPage, fetchList, onSearch, onChip, onView, onMatch, tagList, activeNow };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$d(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_loading_red_line = resolveEasycom(vue.resolveDynamicComponent("loading-red-line"), __easycom_1);
    const _component_app_icon = resolveEasycom(vue.resolveDynamicComponent("app-icon"), __easycom_2$1);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      $setup.loading ? (vue.openBlock(), vue.createBlock(_component_loading_red_line, {
        key: 0,
        top: $setup.statusBarHeight + "px"
      }, null, 8, ["top"])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      vue.createElementVNode("view", { class: "glass-bar topbar" }, [
        vue.createElementVNode("text", { class: "topbar-title" }, "会员库"),
        vue.createElementVNode("view", {
          class: "topbar-btn flex-center",
          "hover-class": "hover-opacity"
        }, [
          vue.createVNode(_component_app_icon, {
            name: "sliders-ink",
            size: "36rpx"
          })
        ])
      ]),
      vue.createElementVNode("view", { class: "search-bar flex items-center" }, [
        vue.createVNode(_component_app_icon, {
          name: "search-ink",
          size: "36rpx"
        }),
        vue.withDirectives(vue.createElementVNode(
          "input",
          {
            "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.keyword = $event),
            class: "search-input",
            placeholder: "搜索姓名、ID、职业...",
            "placeholder-class": "ph",
            "confirm-type": "search",
            onConfirm: $setup.onSearch
          },
          null,
          544
          /* NEED_HYDRATION, NEED_PATCH */
        ), [
          [vue.vModelText, $setup.keyword]
        ]),
        vue.createElementVNode("view", {
          class: "flex-center",
          "hover-class": "hover-opacity",
          style: { "width": "56rpx", "height": "56rpx" }
        }, [
          vue.createVNode(_component_app_icon, {
            name: "mic",
            size: "36rpx"
          })
        ])
      ]),
      vue.createElementVNode("view", { class: "stats-row flex-between" }, [
        vue.createElementVNode("text", { class: "stats-text" }, [
          vue.createTextVNode("共 "),
          vue.createElementVNode(
            "text",
            { class: "stats-num" },
            vue.toDisplayString($setup.total.toLocaleString()),
            1
            /* TEXT */
          ),
          vue.createTextVNode(" 位会员")
        ]),
        vue.createElementVNode("view", { class: "scope-switch flex" }, [
          vue.createElementVNode(
            "view",
            {
              class: vue.normalizeClass(["scope-item flex-center", { "scope-active": $setup.scope === "all" }]),
              onClick: _cache[1] || (_cache[1] = ($event) => $setup.scope = "all")
            },
            "全部",
            2
            /* CLASS */
          ),
          vue.createElementVNode(
            "view",
            {
              class: vue.normalizeClass(["scope-item flex-center", { "scope-active": $setup.scope === "mine" }]),
              onClick: _cache[2] || (_cache[2] = ($event) => $setup.scope = "mine")
            },
            "我负责",
            2
            /* CLASS */
          )
        ])
      ]),
      vue.createElementVNode("scroll-view", {
        "scroll-x": "",
        enhanced: "",
        "show-scrollbar": false,
        class: "chips-scroll"
      }, [
        vue.createElementVNode("view", { class: "flex chips-row" }, [
          (vue.openBlock(), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.chips, (c) => {
              return vue.createElementVNode("view", {
                key: c,
                class: vue.normalizeClass(["chip flex-center", { "chip-active": $setup.activeChip === c }]),
                "hover-class": "hover-opacity",
                onClick: ($event) => $setup.onChip(c)
              }, vue.toDisplayString(c), 11, ["onClick"]);
            }),
            64
            /* STABLE_FRAGMENT */
          )),
          vue.createElementVNode("view", {
            class: "chip chip-more flex-center",
            "hover-class": "hover-opacity"
          }, "+ 更多")
        ])
      ]),
      vue.createElementVNode("view", { class: "member-list" }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.list, (m) => {
            return vue.openBlock(), vue.createBlock(
              _component_glass_card,
              {
                key: m.id,
                strong: "",
                padding: "32rpx",
                radius: "48rpx",
                shadow: "normal",
                class: "member-card",
                "hover-class": "hover-lift-card"
              },
              {
                default: vue.withCtx(() => [
                  vue.createElementVNode("view", {
                    class: "flex",
                    style: { "gap": "28rpx" }
                  }, [
                    vue.createElementVNode("view", { class: "avatar-wrap" }, [
                      vue.createElementVNode(
                        "view",
                        {
                          class: "avatar flex-center",
                          style: vue.normalizeStyle({ background: m.avatarBg, color: "#fff" })
                        },
                        vue.toDisplayString(m.name[0]),
                        5
                        /* TEXT, STYLE */
                      ),
                      m.verified ? (vue.openBlock(), vue.createElementBlock("view", {
                        key: 0,
                        class: "badge-gold flex-center"
                      }, [
                        vue.createVNode(_component_app_icon, {
                          name: "check-white",
                          size: "20rpx"
                        })
                      ])) : vue.createCommentVNode("v-if", true)
                    ]),
                    vue.createElementVNode("view", { class: "member-info" }, [
                      vue.createElementVNode("view", {
                        class: "flex items-center",
                        style: { "gap": "12rpx" }
                      }, [
                        vue.createElementVNode(
                          "text",
                          { class: "member-name" },
                          vue.toDisplayString(m.name) + " · " + vue.toDisplayString(m.age) + "岁",
                          1
                          /* TEXT */
                        ),
                        vue.createElementVNode(
                          "view",
                          {
                            class: vue.normalizeClass(["gender-badge flex-center", m.gender === "女" ? "g-female" : "g-male"])
                          },
                          vue.toDisplayString(m.gender === "女" ? "♀" : "♂"),
                          3
                          /* TEXT, CLASS */
                        )
                      ]),
                      vue.createElementVNode(
                        "text",
                        { class: "member-sub" },
                        vue.toDisplayString(m.job || m.level) + " · " + vue.toDisplayString(m.city) + " · " + vue.toDisplayString(m.education),
                        1
                        /* TEXT */
                      ),
                      vue.createElementVNode("view", {
                        class: "flex flex-wrap",
                        style: { "gap": "12rpx", "margin": "12rpx 0" }
                      }, [
                        (vue.openBlock(true), vue.createElementBlock(
                          vue.Fragment,
                          null,
                          vue.renderList($setup.tagList(m.tags).slice(0, 3), (t) => {
                            return vue.openBlock(), vue.createElementBlock(
                              "text",
                              {
                                key: t,
                                class: "m-tag"
                              },
                              vue.toDisplayString(t),
                              1
                              /* TEXT */
                            );
                          }),
                          128
                          /* KEYED_FRAGMENT */
                        ))
                      ]),
                      vue.createElementVNode("view", {
                        class: "flex items-center",
                        style: { "gap": "8rpx" }
                      }, [
                        $setup.activeNow(m) ? (vue.openBlock(), vue.createElementBlock("view", {
                          key: 0,
                          class: "dot-active"
                        })) : (vue.openBlock(), vue.createBlock(_component_app_icon, {
                          key: 1,
                          name: "clock-warning",
                          size: "24rpx"
                        })),
                        vue.createElementVNode(
                          "text",
                          {
                            class: vue.normalizeClass(["status-text", $setup.activeNow(m) ? "st-active" : "st-idle"])
                          },
                          vue.toDisplayString($setup.activeNow(m) ? "刚刚活跃" : m.lastActive || "待跟进"),
                          3
                          /* TEXT, CLASS */
                        )
                      ])
                    ]),
                    vue.createElementVNode("view", { class: "btn-col" }, [
                      vue.createElementVNode("view", {
                        class: "btn-view flex-center",
                        "hover-class": "hover-opacity",
                        onClick: ($event) => $setup.onView(m)
                      }, "查看", 8, ["onClick"]),
                      vue.createElementVNode("view", {
                        class: "btn-match flex-center",
                        "hover-class": "hover-opacity",
                        onClick: ($event) => $setup.onMatch(m)
                      }, "牵线", 8, ["onClick"])
                    ])
                  ])
                ]),
                _: 2
                /* DYNAMIC */
              },
              1024
              /* DYNAMIC_SLOTS */
            );
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ]),
      vue.createElementVNode("view", { style: { "height": "60rpx" } })
    ]);
  }
  const PagesMembersIndex = /* @__PURE__ */ _export_sfc(_sfc_main$e, [["render", _sfc_render$d], ["__scopeId", "data-v-848428ed"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/members/index.vue"]]);
  const _sfc_main$d = /* @__PURE__ */ vue.defineComponent({
    __name: "ProgressRing",
    props: {
      percent: { type: Number, required: true },
      size: { type: String, required: false, default: "96rpx" },
      stroke: { type: String, required: false, default: "8rpx" }
    },
    setup(__props, { expose: __expose }) {
      __expose();
      const props = __props;
      const deg = vue.computed(() => Math.min(100, Math.max(0, props.percent)) * 3.6);
      const __returned__ = { props, deg };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$c(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock(
      "view",
      {
        class: "ring",
        style: vue.normalizeStyle({
          width: $setup.props.size,
          height: $setup.props.size,
          background: `conic-gradient(#D4AF37 0deg, #E3C766 ${$setup.deg}deg, rgba(139,37,74,0.08) ${$setup.deg}deg 360deg)`
        })
      },
      [
        vue.createElementVNode(
          "view",
          {
            class: "ring-hole flex-center",
            style: vue.normalizeStyle({ margin: $setup.props.stroke })
          },
          [
            vue.createElementVNode("text", { class: "ring-text" }, [
              vue.createTextVNode(
                vue.toDisplayString($props.percent),
                1
                /* TEXT */
              ),
              vue.createElementVNode("text", { class: "ring-pct" }, "%")
            ])
          ],
          4
          /* STYLE */
        )
      ],
      4
      /* STYLE */
    );
  }
  const __easycom_2 = /* @__PURE__ */ _export_sfc(_sfc_main$d, [["render", _sfc_render$c], ["__scopeId", "data-v-56b67a9c"], ["__file", "D:/ai_project/queqiao-workbench/src/components/ProgressRing.vue"]]);
  const _sfc_main$c = /* @__PURE__ */ vue.defineComponent({
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const targetId = vue.ref("");
      const targetName = vue.ref("");
      const recos = vue.ref([]);
      const current = vue.ref(0);
      const todayPairs = vue.ref([]);
      const loading = vue.ref(false);
      const currentReco = vue.computed(() => recos.value[current.value] ?? null);
      const progressPct = vue.computed(
        () => recos.value.length ? Math.round((current.value + 1) / recos.value.length * 100) : 0
      );
      const fetchData = async () => {
        var _a;
        if (!targetId.value)
          return;
        loading.value = true;
        try {
          const [r, p] = await Promise.all([
            api.recommendations(targetId.value),
            api.pairs()
          ]);
          targetName.value = ((_a = r.target) == null ? void 0 : _a.name) || "会员";
          recos.value = r.list || [];
          current.value = 0;
          todayPairs.value = p.list.slice(0, 2);
        } catch {
          uni.showToast({ title: "后端未连接", icon: "none" });
        } finally {
          loading.value = false;
        }
      };
      onLoad((q) => {
        const id = String((q == null ? void 0 : q.memberId) || "");
        if (id) {
          targetId.value = id;
          targetName.value = (q == null ? void 0 : q.memberName) ? String(q.memberName) : "";
          uni.setNavigationBarTitle({ title: `为「${targetName.value || "会员"}」牵线` });
        }
        fetchData();
      });
      onShow(() => {
        if (targetId.value)
          fetchData();
      });
      onPullDownRefresh(() => {
        if (targetId.value)
          fetchData();
      });
      const onSkip = () => {
        if (current.value < recos.value.length - 1)
          current.value++;
        else
          uni.showToast({ title: "今日推荐已看完", icon: "none" });
      };
      const onLike = async () => {
        const r = currentReco.value;
        if (!r)
          return;
        try {
          await api.likeRecommendation(targetId.value, r.member.id);
        } catch {
        }
        uni.showToast({ title: `已喜欢 ${r.member.name}`, icon: "none" });
        onSkip();
      };
      const onMatch = async () => {
        const r = currentReco.value;
        if (!r)
          return;
        try {
          await api.createPair(
            r.member.gender === "男" ? r.member.id : targetId.value,
            r.member.gender === "男" ? targetId.value : r.member.id
          );
          uni.showToast({ title: `已向 ${r.member.name} 发起牵线`, icon: "success" });
        } catch {
          uni.showToast({ title: "发起失败", icon: "none" });
        }
        onSkip();
      };
      const onRefresh = () => fetchData();
      const goMembers = () => uni.switchTab({ url: "/pages/members/index" });
      const __returned__ = { statusBarHeight, targetId, targetName, recos, current, todayPairs, loading, currentReco, progressPct, fetchData, onSkip, onLike, onMatch, onRefresh, goMembers };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$b(_ctx, _cache, $props, $setup, $data, $options) {
    var _a;
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_loading_red_line = resolveEasycom(vue.resolveDynamicComponent("loading-red-line"), __easycom_1);
    const _component_app_icon = resolveEasycom(vue.resolveDynamicComponent("app-icon"), __easycom_2$1);
    const _component_progress_ring = resolveEasycom(vue.resolveDynamicComponent("progress-ring"), __easycom_2);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      $setup.loading ? (vue.openBlock(), vue.createBlock(_component_loading_red_line, {
        key: 0,
        top: $setup.statusBarHeight + "px"
      }, null, 8, ["top"])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      vue.createElementVNode("view", { class: "glass-bar topbar" }, [
        vue.createElementVNode("text", { class: "topbar-title" }, "智能牵线"),
        vue.createElementVNode("view", {
          class: "circle-btn flex-center",
          "hover-class": "hover-opacity"
        }, [
          vue.createVNode(_component_app_icon, {
            name: "history-ink",
            size: "36rpx"
          })
        ])
      ]),
      !$setup.targetId && !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "empty-target flex-center"
      }, [
        vue.createElementVNode("text", { class: "empty-target-text" }, "请从「会员库」选择一位会员发起智能牵线"),
        vue.createElementVNode("view", {
          class: "empty-target-btn flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.goMembers
        }, "去会员库")
      ])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("view", { class: "ai-banner flex items-center" }, [
        vue.createElementVNode("view", { class: "ai-icon flex-center" }, [
          vue.createVNode(_component_app_icon, {
            name: "sparkles-white",
            size: "36rpx"
          })
        ]),
        vue.createElementVNode("view", { class: "flex-1" }, [
          vue.createElementVNode("text", { class: "ai-title" }, "今日 AI 推荐"),
          vue.createElementVNode("text", { class: "ai-sub" }, [
            vue.createTextVNode("基于128项维度，为你筛选出 "),
            vue.createElementVNode(
              "text",
              { class: "ai-num" },
              vue.toDisplayString($setup.recos.length),
              1
              /* TEXT */
            ),
            vue.createTextVNode(" 位高匹配度会员")
          ])
        ]),
        vue.createElementVNode("view", {
          class: "refresh-btn flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.onRefresh
        }, "刷新")
      ]),
      $setup.recos.length ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "progress-row flex-between"
      }, [
        vue.createElementVNode(
          "text",
          { class: "progress-text" },
          "推荐 " + vue.toDisplayString($setup.current + 1) + " / " + vue.toDisplayString($setup.recos.length),
          1
          /* TEXT */
        ),
        vue.createElementVNode("view", { class: "progress-track" }, [
          vue.createElementVNode(
            "view",
            {
              class: "progress-fill",
              style: vue.normalizeStyle({ width: $setup.progressPct + "%" })
            },
            null,
            4
            /* STYLE */
          )
        ])
      ])) : vue.createCommentVNode("v-if", true),
      $setup.currentReco ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 3,
        class: "stack-zone"
      }, [
        vue.createElementVNode("view", { class: "back-layer layer-2" }),
        vue.createElementVNode("view", { class: "back-layer layer-1" }),
        vue.createElementVNode("view", { class: "main-card" }, [
          vue.createElementVNode(
            "view",
            {
              class: "photo-area",
              style: vue.normalizeStyle({ background: $setup.currentReco.member.avatarBg })
            },
            [
              vue.createElementVNode(
                "text",
                { class: "photo-char" },
                vue.toDisplayString($setup.currentReco.member.name[0]),
                1
                /* TEXT */
              ),
              vue.createElementVNode("view", { class: "photo-certs" }, [
                $setup.currentReco.member.verified ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 0,
                  class: "photo-cert flex items-center"
                }, [
                  vue.createVNode(_component_app_icon, {
                    name: "shield-white",
                    size: "22rpx"
                  }),
                  vue.createElementVNode("text", null, "已实名认证")
                ])) : vue.createCommentVNode("v-if", true),
                ((_a = $setup.currentReco.member.certs) == null ? void 0 : _a.includes("学历认证")) ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 1,
                  class: "photo-cert flex items-center"
                }, [
                  vue.createVNode(_component_app_icon, {
                    name: "graduation-white",
                    size: "22rpx"
                  }),
                  vue.createElementVNode("text", null, "已学历认证")
                ])) : vue.createCommentVNode("v-if", true)
              ]),
              vue.createElementVNode("view", { class: "photo-ring" }, [
                vue.createVNode(_component_progress_ring, {
                  percent: $setup.currentReco.score,
                  size: "104rpx",
                  stroke: "10rpx"
                }, null, 8, ["percent"]),
                vue.createElementVNode("text", { class: "ring-caption" }, "匹配度")
              ]),
              vue.createElementVNode("view", { class: "photo-name flex items-baseline" }, [
                vue.createElementVNode(
                  "text",
                  { class: "pn-name" },
                  vue.toDisplayString($setup.currentReco.member.name),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "pn-age" },
                  "· " + vue.toDisplayString($setup.currentReco.member.age) + "岁",
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "pn-gender" },
                  vue.toDisplayString($setup.currentReco.member.gender === "女" ? "♀" : "♂"),
                  1
                  /* TEXT */
                )
              ])
            ],
            4
            /* STYLE */
          ),
          vue.createElementVNode("view", { class: "info-area" }, [
            vue.createElementVNode("view", {
              class: "flex flex-wrap",
              style: { "gap": "16rpx", "margin-bottom": "16rpx" }
            }, [
              vue.createElementVNode(
                "text",
                { class: "info-tag" },
                vue.toDisplayString($setup.currentReco.member.city),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "info-tag" },
                vue.toDisplayString($setup.currentReco.member.job),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "info-tag" },
                vue.toDisplayString($setup.currentReco.member.education),
                1
                /* TEXT */
              ),
              $setup.currentReco.member.height ? (vue.openBlock(), vue.createElementBlock(
                "text",
                {
                  key: 0,
                  class: "info-tag"
                },
                vue.toDisplayString($setup.currentReco.member.height) + "cm",
                1
                /* TEXT */
              )) : vue.createCommentVNode("v-if", true)
            ]),
            vue.createElementVNode(
              "text",
              { class: "info-intro" },
              vue.toDisplayString($setup.currentReco.member.intro),
              1
              /* TEXT */
            ),
            vue.createElementVNode("view", { class: "hl-list" }, [
              (vue.openBlock(true), vue.createElementBlock(
                vue.Fragment,
                null,
                vue.renderList($setup.currentReco.reasons, (h) => {
                  return vue.openBlock(), vue.createElementBlock("view", {
                    key: h,
                    class: "hl-item flex items-center"
                  }, [
                    vue.createVNode(_component_app_icon, {
                      name: "sparkles-gold",
                      size: "20rpx"
                    }),
                    vue.createElementVNode(
                      "text",
                      null,
                      vue.toDisplayString(h),
                      1
                      /* TEXT */
                    )
                  ]);
                }),
                128
                /* KEYED_FRAGMENT */
              ))
            ])
          ])
        ])
      ])) : vue.createCommentVNode("v-if", true),
      $setup.currentReco ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 4,
        class: "actions flex-center"
      }, [
        vue.createElementVNode("view", {
          class: "act-circle act-skip flex-center",
          "hover-class": "hover-scale",
          onClick: $setup.onSkip
        }, [
          vue.createVNode(_component_app_icon, {
            name: "x-ink",
            size: "44rpx"
          })
        ]),
        vue.createElementVNode("view", {
          class: "act-circle act-main flex-center anim-heartbeat",
          "hover-class": "hover-scale",
          onClick: $setup.onMatch
        }, [
          vue.createVNode(_component_app_icon, {
            name: "handshake-white",
            size: "56rpx"
          })
        ]),
        vue.createElementVNode("view", {
          class: "act-circle act-like flex-center",
          "hover-class": "hover-scale",
          onClick: $setup.onLike
        }, [
          vue.createVNode(_component_app_icon, {
            name: "heart-white",
            size: "44rpx"
          })
        ])
      ])) : vue.createCommentVNode("v-if", true),
      $setup.currentReco ? (vue.openBlock(), vue.createElementBlock("text", {
        key: 5,
        class: "act-tip"
      }, "点击大按钮发起牵线")) : vue.createCommentVNode("v-if", true),
      $setup.todayPairs.length ? (vue.openBlock(), vue.createBlock(_component_glass_card, {
        key: 6,
        padding: "32rpx",
        radius: "40rpx",
        class: "today-card"
      }, {
        default: vue.withCtx(() => [
          vue.createElementVNode("view", {
            class: "flex items-center",
            style: { "gap": "12rpx" }
          }, [
            vue.createVNode(_component_app_icon, {
              name: "check-circle-success",
              size: "32rpx"
            }),
            vue.createElementVNode("text", { class: "today-title" }, "今日已牵线")
          ]),
          vue.createElementVNode("view", { class: "today-divider" }),
          (vue.openBlock(true), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.todayPairs, (p) => {
              var _a2, _b, _c, _d, _e, _f, _g, _h;
              return vue.openBlock(), vue.createElementBlock("view", {
                key: p.id,
                class: "today-row flex-between"
              }, [
                vue.createElementVNode("view", {
                  class: "flex items-center",
                  style: { "gap": "16rpx" }
                }, [
                  vue.createElementVNode("view", { class: "duo-wrap" }, [
                    vue.createElementVNode(
                      "view",
                      {
                        class: "duo d-1 flex-center",
                        style: vue.normalizeStyle({ background: (_a2 = p.female) == null ? void 0 : _a2.avatarBg })
                      },
                      vue.toDisplayString((_c = (_b = p.female) == null ? void 0 : _b.name) == null ? void 0 : _c[0]),
                      5
                      /* TEXT, STYLE */
                    ),
                    vue.createElementVNode(
                      "view",
                      {
                        class: "duo d-2 flex-center",
                        style: vue.normalizeStyle({ background: (_d = p.male) == null ? void 0 : _d.avatarBg })
                      },
                      vue.toDisplayString((_f = (_e = p.male) == null ? void 0 : _e.name) == null ? void 0 : _f[0]),
                      5
                      /* TEXT, STYLE */
                    )
                  ]),
                  vue.createElementVNode(
                    "text",
                    { class: "today-names" },
                    vue.toDisplayString((_g = p.female) == null ? void 0 : _g.name) + " × " + vue.toDisplayString((_h = p.male) == null ? void 0 : _h.name),
                    1
                    /* TEXT */
                  )
                ]),
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["today-status", p.status === "已约见" ? "ts-success" : "ts-warning"])
                  },
                  vue.toDisplayString(p.statusLabel),
                  3
                  /* TEXT, CLASS */
                )
              ]);
            }),
            128
            /* KEYED_FRAGMENT */
          ))
        ]),
        _: 1
        /* STABLE */
      })) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode("view", { style: { "height": "60rpx" } })
    ]);
  }
  const PagesMatchIndex = /* @__PURE__ */ _export_sfc(_sfc_main$c, [["render", _sfc_render$b], ["__scopeId", "data-v-fd66127e"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/match/index.vue"]]);
  const _sfc_main$b = /* @__PURE__ */ vue.defineComponent({
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const segs = [
        { key: "", label: "全部" },
        { key: "member", label: "会员消息" },
        { key: "system", label: "系统通知" }
      ];
      const activeSeg = vue.ref("");
      const list = vue.ref([]);
      const loading = vue.ref(false);
      const fetchList = async () => {
        var _a;
        loading.value = true;
        try {
          const res = await api.conversations(activeSeg.value || void 0);
          list.value = res.list;
        } catch {
          uni.showToast({ title: "后端未连接", icon: "none" });
        } finally {
          loading.value = false;
          (_a = uni.stopPullDownRefresh) == null ? void 0 : _a.call(uni);
        }
      };
      onShow(fetchList);
      onPullDownRefresh(fetchList);
      const onSeg = (key) => {
        activeSeg.value = key;
        fetchList();
      };
      const iconOf = (c) => c.icon === "sparkles" ? "sparkles-white" : c.icon === "megaphone" ? "megaphone-gold" : "users-primary";
      const onOpen = (c) => {
        c.unread = 0;
        uni.showToast({ title: `打开与「${c.from}」的会话`, icon: "none" });
      };
      const __returned__ = { statusBarHeight, segs, activeSeg, list, loading, fetchList, onSeg, iconOf, onOpen };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$a(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_loading_red_line = resolveEasycom(vue.resolveDynamicComponent("loading-red-line"), __easycom_1);
    const _component_app_icon = resolveEasycom(vue.resolveDynamicComponent("app-icon"), __easycom_2$1);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      $setup.loading ? (vue.openBlock(), vue.createBlock(_component_loading_red_line, {
        key: 0,
        top: $setup.statusBarHeight + "px"
      }, null, 8, ["top"])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      vue.createElementVNode("view", { class: "glass-bar topbar" }, [
        vue.createElementVNode("text", { class: "topbar-title" }, "消息"),
        vue.createElementVNode("view", {
          class: "circle-btn flex-center",
          "hover-class": "hover-opacity"
        }, [
          vue.createVNode(_component_app_icon, {
            name: "pen-ink",
            size: "36rpx"
          })
        ])
      ]),
      vue.createElementVNode("view", { class: "bento quick-bento" }, [
        vue.createVNode(_component_glass_card, {
          padding: "28rpx",
          radius: "var(--q-bento-radius)",
          shadow: "soft",
          class: "qc",
          "hover-class": "hover-lift-card"
        }, {
          default: vue.withCtx(() => [
            vue.createElementVNode("view", {
              class: "flex items-center",
              style: { "gap": "20rpx" }
            }, [
              vue.createElementVNode("view", { class: "qc-icon qc-ai flex-center anim-breathe" }, [
                vue.createVNode(_component_app_icon, {
                  name: "bot-white",
                  size: "36rpx"
                })
              ]),
              vue.createElementVNode("view", null, [
                vue.createElementVNode("text", { class: "qc-title" }, "AI 红娘助手"),
                vue.createElementVNode("text", { class: "qc-sub" }, "智能回复建议")
              ])
            ])
          ]),
          _: 1
          /* STABLE */
        }),
        vue.createVNode(_component_glass_card, {
          padding: "28rpx",
          radius: "var(--q-bento-radius)",
          shadow: "soft",
          class: "qc",
          "hover-class": "hover-lift-card"
        }, {
          default: vue.withCtx(() => [
            vue.createElementVNode("view", {
              class: "flex items-center",
              style: { "gap": "20rpx" }
            }, [
              vue.createElementVNode("view", { class: "qc-icon qc-sys flex-center" }, [
                vue.createVNode(_component_app_icon, {
                  name: "bell-gold",
                  size: "36rpx"
                })
              ]),
              vue.createElementVNode("view", null, [
                vue.createElementVNode("text", { class: "qc-title" }, "系统通知"),
                vue.createElementVNode("text", { class: "qc-sub qc-unread" }, "3条未读")
              ])
            ])
          ]),
          _: 1
          /* STABLE */
        })
      ]),
      vue.createElementVNode("view", { class: "seg" }, [
        (vue.openBlock(), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.segs, (s2) => {
            return vue.createElementVNode("view", {
              key: s2.key,
              class: vue.normalizeClass(["seg-item flex-center", { "seg-active": $setup.activeSeg === s2.key }]),
              onClick: ($event) => $setup.onSeg(s2.key)
            }, vue.toDisplayString(s2.label), 11, ["onClick"]);
          }),
          64
          /* STABLE_FRAGMENT */
        ))
      ]),
      vue.createElementVNode("view", { class: "chat-list" }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.list, (c) => {
            return vue.openBlock(), vue.createElementBlock("view", {
              key: c.id,
              class: "chat-item flex items-center",
              "hover-class": "chat-hover",
              onClick: ($event) => $setup.onOpen(c)
            }, [
              vue.createElementVNode("view", { class: "avatar-wrap" }, [
                c.avatarText ? (vue.openBlock(), vue.createElementBlock(
                  "view",
                  {
                    key: 0,
                    class: "chat-avatar flex-center",
                    style: vue.normalizeStyle({ background: c.avatarBg, color: c.avatarColor })
                  },
                  vue.toDisplayString(c.avatarText),
                  5
                  /* TEXT, STYLE */
                )) : (vue.openBlock(), vue.createElementBlock(
                  "view",
                  {
                    key: 1,
                    class: vue.normalizeClass(["chat-avatar flex-center", c.kind === "ai" ? "ca-ai" : c.kind === "system" ? "ca-sys" : "ca-group"])
                  },
                  [
                    vue.createVNode(_component_app_icon, {
                      name: $setup.iconOf(c),
                      size: "40rpx"
                    }, null, 8, ["name"])
                  ],
                  2
                  /* CLASS */
                )),
                c.unread > 0 ? (vue.openBlock(), vue.createElementBlock(
                  "view",
                  {
                    key: 2,
                    class: "unread-badge flex-center"
                  },
                  vue.toDisplayString(c.unread),
                  1
                  /* TEXT */
                )) : vue.createCommentVNode("v-if", true)
              ]),
              vue.createElementVNode("view", { class: "chat-body" }, [
                vue.createElementVNode("view", { class: "flex-between items-center" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "chat-name" },
                    vue.toDisplayString(c.from),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "chat-time" },
                    vue.toDisplayString(c.time),
                    1
                    /* TEXT */
                  )
                ]),
                vue.createElementVNode(
                  "text",
                  { class: "chat-snippet" },
                  vue.toDisplayString(c.snippet),
                  1
                  /* TEXT */
                )
              ])
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ]),
      vue.createElementVNode("view", { style: { "height": "40rpx" } })
    ]);
  }
  const PagesMessageIndex = /* @__PURE__ */ _export_sfc(_sfc_main$b, [["render", _sfc_render$a], ["__scopeId", "data-v-bdafde18"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/message/index.vue"]]);
  const _sfc_main$a = /* @__PURE__ */ vue.defineComponent({
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const me = getStoredUser();
      const initial = vue.computed(() => ((me == null ? void 0 : me.name) || "鹊")[0]);
      const avatarBg = vue.computed(() => (me == null ? void 0 : me.avatarBg) || "linear-gradient(135deg, #E3C766 0%, #D4AF37 48%, #B8941F 100%)");
      const roleLabel = vue.computed(
        () => (me == null ? void 0 : me.role) === "ADMIN" ? "管理员" : (me == null ? void 0 : me.role) === "MANAGER" ? "主管" : "金牌红娘"
      );
      const maskedPhone = vue.computed(() => {
        const p = (me == null ? void 0 : me.phone) || "";
        return p.replace(/(\d{3})\d{4}(\d{4})/, "$1****$2") || "未绑定手机";
      });
      const stats = vue.ref([
        { label: "今日约见", value: "—", icon: "stat-calendar" },
        { label: "牵线中", value: "—", icon: "stat-match" },
        { label: "已脱单", value: "—", icon: "heart-white" }
      ]);
      const fetchStats = async () => {
        try {
          const [, pairRes] = await Promise.all([api.calendarToday(), api.pairs({ size: 100 })]);
          const today = pairRes.list.filter((p) => p.status === "已约见" || p.status === "待反馈");
          const done = pairRes.list.filter((p) => p.status === "已脱单" || p.stage === "已脱单");
          stats.value = [
            { label: "今日约见", value: String(today.length), icon: "stat-calendar" },
            { label: "牵线中", value: String(pairRes.list.length - done.length), icon: "stat-match" },
            { label: "已脱单", value: String(done.length), icon: "heart-white" }
          ];
        } catch {
        }
      };
      onShow(fetchStats);
      const businessMenus = [
        { icon: "users-primary", title: "会员库", sub: "建档 / 检索 / 跟进", to: "/pages/members/index", tab: true },
        { icon: "handshake-white", title: "智能牵线", sub: "AI 匹配推荐", to: "/pages/match/index", tab: true },
        { icon: "stat-calendar", title: "约见日程", sub: "安排与提醒", to: "/pages/schedule/index", tab: false },
        { icon: "act-chart", title: "跟进看板", sub: "牵线进度一览", to: "/pages/followup/kanban", tab: false }
      ];
      const accountMenus = [
        { icon: "message-primary", title: "消息中心", sub: "会员消息 / 系统通知", to: "/pages/message/index", tab: true },
        { icon: "pen-ink", title: "编辑资料", sub: "姓名 / 手机 / 邮箱", action: "edit" },
        { icon: "shield-white", title: "安全设置", sub: "修改登录密码", action: "password" }
      ];
      const onMenu = (m) => {
        if (m.action === "edit") {
          uni.showToast({ title: "编辑资料（开发中）", icon: "none" });
          return;
        }
        if (m.action === "password") {
          uni.showToast({ title: "修改密码（开发中）", icon: "none" });
          return;
        }
        if (m.tab) {
          uni.switchTab({ url: m.to });
        } else {
          uni.navigateTo({ url: m.to });
        }
      };
      const onLogout = () => {
        uni.showModal({
          title: "退出登录",
          content: "确定要退出当前账号吗？",
          confirmColor: "#C2405A",
          success: (res) => {
            if (!res.confirm)
              return;
            api.logout().catch(() => {
            });
            clearAuth();
            uni.reLaunch({ url: "/pages/login/index" });
          }
        });
      };
      const __returned__ = { statusBarHeight, me, initial, avatarBg, roleLabel, maskedPhone, stats, fetchStats, businessMenus, accountMenus, onMenu, onLogout };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$9(_ctx, _cache, $props, $setup, $data, $options) {
    var _a;
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_app_icon = resolveEasycom(vue.resolveDynamicComponent("app-icon"), __easycom_2$1);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      vue.createElementVNode("view", { class: "glass-bar topbar" }, [
        vue.createElementVNode("text", { class: "topbar-title" }, "我的"),
        vue.createElementVNode("view", {
          class: "circle-btn flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.onLogout
        }, [
          vue.createVNode(_component_app_icon, {
            name: "share-ink",
            size: "34rpx"
          })
        ])
      ]),
      vue.createElementVNode("view", { class: "profile-card" }, [
        vue.createElementVNode(
          "view",
          {
            class: "pc-avatar flex-center",
            style: vue.normalizeStyle({ background: $setup.avatarBg })
          },
          [
            vue.createElementVNode(
              "text",
              { class: "pc-initial" },
              vue.toDisplayString($setup.initial),
              1
              /* TEXT */
            )
          ],
          4
          /* STYLE */
        ),
        vue.createElementVNode(
          "text",
          { class: "pc-name" },
          vue.toDisplayString(((_a = $setup.me) == null ? void 0 : _a.name) || "红娘"),
          1
          /* TEXT */
        ),
        vue.createElementVNode("view", {
          class: "flex items-center",
          style: { "gap": "12rpx", "margin-top": "14rpx" }
        }, [
          vue.createElementVNode(
            "view",
            { class: "pc-role flex-center" },
            vue.toDisplayString($setup.roleLabel),
            1
            /* TEXT */
          ),
          vue.createElementVNode(
            "text",
            { class: "pc-phone" },
            vue.toDisplayString($setup.maskedPhone),
            1
            /* TEXT */
          )
        ])
      ]),
      vue.createElementVNode("view", { class: "bento stat-bento" }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.stats, (s2, i) => {
            return vue.openBlock(), vue.createBlock(_component_glass_card, {
              key: s2.label,
              padding: "28rpx",
              radius: "var(--q-bento-radius)",
              shadow: "soft",
              class: vue.normalizeClass(["sc", { "sc-hero": i === 0 }])
            }, {
              default: vue.withCtx(() => [
                vue.createElementVNode(
                  "view",
                  {
                    class: vue.normalizeClass(["sc-icon flex-center", "sc-tone-" + (i + 1)])
                  },
                  [
                    vue.createVNode(_component_app_icon, {
                      name: s2.icon,
                      size: "32rpx"
                    }, null, 8, ["name"])
                  ],
                  2
                  /* CLASS */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "sc-label" },
                  vue.toDisplayString(s2.label),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["sc-value", { "sc-value-hero": i === 0 }])
                  },
                  vue.toDisplayString(s2.value),
                  3
                  /* TEXT, CLASS */
                )
              ]),
              _: 2
              /* DYNAMIC */
            }, 1032, ["class"]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ]),
      vue.createElementVNode("view", { class: "menu-card glass-card-strong" }, [
        vue.createElementVNode("text", { class: "menu-title" }, "业务"),
        (vue.openBlock(), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.businessMenus, (m, i) => {
            return vue.createElementVNode("view", {
              key: m.title,
              class: "menu-row flex items-center",
              "hover-class": "menu-hover",
              onClick: ($event) => $setup.onMenu(m)
            }, [
              vue.createElementVNode(
                "view",
                {
                  class: vue.normalizeClass(["menu-ico flex-center", { "menu-ico-gold": i === 0 }])
                },
                [
                  vue.createVNode(_component_app_icon, {
                    name: m.icon,
                    size: "34rpx"
                  }, null, 8, ["name"])
                ],
                2
                /* CLASS */
              ),
              vue.createElementVNode("view", { class: "menu-body" }, [
                vue.createElementVNode(
                  "text",
                  { class: "menu-name" },
                  vue.toDisplayString(m.title),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "menu-sub" },
                  vue.toDisplayString(m.sub),
                  1
                  /* TEXT */
                )
              ]),
              vue.createVNode(_component_app_icon, {
                name: "chevron-right",
                size: "26rpx"
              })
            ], 8, ["onClick"]);
          }),
          64
          /* STABLE_FRAGMENT */
        ))
      ]),
      vue.createElementVNode("view", { class: "menu-card glass-card-strong" }, [
        vue.createElementVNode("text", { class: "menu-title" }, "账号"),
        (vue.openBlock(), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.accountMenus, (m) => {
            return vue.createElementVNode("view", {
              key: m.title,
              class: "menu-row flex items-center",
              "hover-class": "menu-hover",
              onClick: ($event) => $setup.onMenu(m)
            }, [
              vue.createElementVNode("view", { class: "menu-ico flex-center" }, [
                vue.createVNode(_component_app_icon, {
                  name: m.icon,
                  size: "34rpx"
                }, null, 8, ["name"])
              ]),
              vue.createElementVNode("view", { class: "menu-body" }, [
                vue.createElementVNode(
                  "text",
                  { class: "menu-name" },
                  vue.toDisplayString(m.title),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "menu-sub" },
                  vue.toDisplayString(m.sub),
                  1
                  /* TEXT */
                )
              ]),
              vue.createVNode(_component_app_icon, {
                name: "chevron-right",
                size: "26rpx"
              })
            ], 8, ["onClick"]);
          }),
          64
          /* STABLE_FRAGMENT */
        ))
      ]),
      vue.createElementVNode("view", {
        class: "logout-btn flex-center",
        "hover-class": "hover-opacity",
        onClick: $setup.onLogout
      }, [
        vue.createVNode(_component_app_icon, {
          name: "x-ink",
          size: "30rpx"
        }),
        vue.createElementVNode("text", null, "退出登录")
      ]),
      vue.createElementVNode("text", { class: "version" }, "鹊桥 · 红娘工作台 v1.0.0"),
      vue.createElementVNode("view", { style: { "height": "48rpx" } })
    ]);
  }
  const PagesProfileIndex = /* @__PURE__ */ _export_sfc(_sfc_main$a, [["render", _sfc_render$9], ["__scopeId", "data-v-f97f9319"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/profile/index.vue"]]);
  const _sfc_main$9 = /* @__PURE__ */ vue.defineComponent({
    __name: "input",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const editId = vue.ref("");
      const isEdit = vue.computed(() => !!editId.value);
      const form = vue.ref({
        name: "",
        age: void 0,
        gender: "男",
        phone: "",
        city: "",
        job: "",
        education: "",
        height: void 0,
        income: "",
        level: "",
        requirement: ""
      });
      const customTags = vue.ref([]);
      const tagInput = vue.ref("");
      const addTag = () => {
        const t = tagInput.value.trim();
        if (t && !customTags.value.includes(t) && customTags.value.length < 10) {
          customTags.value.push(t);
        }
        tagInput.value = "";
      };
      const removeTag = (t) => {
        customTags.value = customTags.value.filter((x) => x !== t);
      };
      const EDUCATION_OPTS = ["高中", "大专", "本科", "硕士", "博士"];
      const LEVEL_OPTS = ["普通", "金卡", "VIP", "SVIP", "钻石VIP"];
      const backfill = (m) => {
        form.value.name = m.name || "";
        form.value.age = m.age;
        form.value.gender = m.gender === "女" ? "女" : "男";
        form.value.phone = m.phone || "";
        form.value.city = m.city || "";
        form.value.job = m.job || "";
        form.value.education = m.education || "";
        form.value.height = m.height;
        form.value.income = m.income || "";
        form.value.level = m.level || "";
        form.value.requirement = m.requirement || "";
        customTags.value = (m.tags ? m.tags.split(/[,，、]/).filter(Boolean) : []).slice(0, 10);
      };
      onLoad(async (q) => {
        const id = String((q == null ? void 0 : q.id) || "");
        if (id) {
          editId.value = id;
          uni.setNavigationBarTitle({ title: "编辑会员资料" });
          try {
            const r = await api.memberDetail(id);
            backfill(r.member);
          } catch (e) {
            uni.showToast({ title: errMsg(e), icon: "none" });
          }
        }
      });
      const aiFields = vue.ref([]);
      const panelOpen = vue.ref(false);
      const parseModal = vue.ref(false);
      const parseText = vue.ref("");
      const parsing = vue.ref(false);
      const FIELD_LABELS = {
        name: "姓名",
        age: "年龄",
        gender: "性别",
        phone: "手机号",
        city: "城市",
        job: "职业",
        education: "学历",
        height: "身高",
        income: "收入",
        level: "等级",
        tags: "标签"
      };
      const parseNumberSafe = (v) => {
        if (v === null || v === void 0 || v === "")
          return void 0;
        const n2 = typeof v === "number" ? v : parseInt(String(v).replace(/\D/g, ""), 10);
        return Number.isFinite(n2) ? n2 : void 0;
      };
      const normalizeGender = (v) => {
        const s2 = String(v || "").replace(/性$/, "");
        return s2 === "男" || s2 === "女" ? s2 : void 0;
      };
      const doParse = async () => {
        if (!parseText.value.trim() || parsing.value)
          return;
        parsing.value = true;
        try {
          const res = await api.aiParse(parseText.value.trim());
          const f = res.fields || {};
          const adopted = /* @__PURE__ */ new Set();
          if (f.name) {
            form.value.name = f.name;
            adopted.add("name");
          }
          const age = parseNumberSafe(f.age);
          if (age !== void 0) {
            form.value.age = age;
            adopted.add("age");
          }
          const gender = normalizeGender(f.gender);
          if (gender) {
            form.value.gender = gender;
            adopted.add("gender");
          }
          if (f.phone) {
            form.value.phone = String(f.phone);
            adopted.add("phone");
          }
          if (f.city) {
            form.value.city = f.city;
            adopted.add("city");
          }
          if (f.job) {
            form.value.job = f.job;
            adopted.add("job");
          }
          if (f.education) {
            form.value.education = f.education;
            adopted.add("education");
          }
          const height = parseNumberSafe(f.height);
          if (height !== void 0) {
            form.value.height = height;
            adopted.add("height");
          }
          if (f.income) {
            form.value.income = f.income;
            adopted.add("income");
          }
          if (f.level) {
            form.value.level = f.level;
            adopted.add("level");
          }
          if (Array.isArray(f.tags) && f.tags.length) {
            customTags.value = [.../* @__PURE__ */ new Set([...customTags.value, ...f.tags.map(String)])].slice(0, 10);
            adopted.add("tags");
          }
          aiFields.value = Object.entries(f).filter(([, v]) => v !== null && v !== void 0 && v !== "").map(([k, v]) => ({
            key: k,
            label: FIELD_LABELS[k] || k,
            value: Array.isArray(v) ? v.join("、") : String(v),
            adopted: adopted.has(k)
          }));
          parseModal.value = false;
          panelOpen.value = true;
          uni.showToast({ title: `AI 识别 ${aiFields.value.length} 项信息`, icon: "none" });
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          parsing.value = false;
        }
      };
      const saving = vue.ref(false);
      const onCancel = () => uni.navigateBack();
      const buildPayload = () => {
        const f = form.value;
        return {
          name: f.name,
          age: f.age,
          gender: f.gender,
          phone: f.phone,
          city: f.city,
          job: f.job,
          education: f.education,
          height: f.height,
          income: f.income,
          level: f.level,
          requirement: f.requirement,
          tags: customTags.value.join(",")
        };
      };
      const onSave = async () => {
        if (saving.value)
          return;
        const f = form.value;
        if (!f.name.trim()) {
          uni.showToast({ title: "请填写姓名", icon: "none" });
          return;
        }
        if (f.age !== void 0 && (f.age < 1 || f.age > 120)) {
          uni.showToast({ title: "年龄需在 1-120 之间", icon: "none" });
          return;
        }
        const phoneDigits = f.phone.replace(/\D/g, "");
        if (phoneDigits && phoneDigits.length !== 11) {
          uni.showToast({ title: "手机号需为 11 位", icon: "none" });
          return;
        }
        saving.value = true;
        try {
          const payload = buildPayload();
          if (isEdit.value) {
            await api.updateMember(editId.value, payload);
          } else {
            await api.createMember(payload);
          }
          uni.showToast({ title: "已保存", icon: "success" });
          setTimeout(() => uni.navigateBack(), 500);
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          saving.value = false;
        }
      };
      const __returned__ = { statusBarHeight, editId, isEdit, form, customTags, tagInput, addTag, removeTag, EDUCATION_OPTS, LEVEL_OPTS, backfill, aiFields, panelOpen, parseModal, parseText, parsing, FIELD_LABELS, parseNumberSafe, normalizeGender, doParse, saving, onCancel, buildPayload, onSave };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$8(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_loading_red_line = resolveEasycom(vue.resolveDynamicComponent("loading-red-line"), __easycom_1);
    const _component_app_icon = resolveEasycom(vue.resolveDynamicComponent("app-icon"), __easycom_2$1);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      $setup.saving ? (vue.openBlock(), vue.createBlock(_component_loading_red_line, {
        key: 0,
        top: $setup.statusBarHeight + "px"
      }, null, 8, ["top"])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      vue.createElementVNode("view", { class: "glass-bar nav-bar" }, [
        vue.createElementVNode("view", {
          class: "nav-back flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.onCancel
        }, [
          vue.createVNode(_component_app_icon, {
            name: "back-ink",
            size: "44rpx"
          })
        ]),
        vue.createElementVNode(
          "text",
          { class: "nav-title" },
          vue.toDisplayString($setup.isEdit ? "编辑会员资料" : "智能录入与编辑"),
          1
          /* TEXT */
        ),
        vue.createElementVNode("view", { style: { "width": "64rpx" } })
      ]),
      vue.createElementVNode("view", {
        class: "ai-bar flex-center",
        "hover-class": "hover-scale",
        onClick: _cache[0] || (_cache[0] = ($event) => $setup.panelOpen = true)
      }, [
        vue.createVNode(_component_app_icon, {
          name: "edit-gold",
          size: "28rpx"
        }),
        vue.createElementVNode(
          "text",
          { class: "ai-bar-text" },
          "AI已识别 " + vue.toDisplayString($setup.aiFields.length) + " 项信息，请确认",
          1
          /* TEXT */
        ),
        vue.createVNode(_component_app_icon, {
          name: "chevron-right",
          size: "26rpx"
        })
      ]),
      vue.createElementVNode("view", {
        class: "paste-entry flex-center",
        "hover-class": "hover-opacity",
        onClick: _cache[1] || (_cache[1] = ($event) => $setup.parseModal = true)
      }, [
        vue.createVNode(_component_app_icon, {
          name: "sparkles-gold",
          size: "26rpx"
        }),
        vue.createElementVNode("text", { class: "paste-text" }, "粘贴文本 / 语音转写，AI 自动填表")
      ]),
      vue.createElementVNode("view", { class: "form-body" }, [
        vue.createVNode(_component_glass_card, {
          strong: "",
          padding: "36rpx",
          shadow: "normal",
          class: "group-card"
        }, {
          default: vue.withCtx(() => [
            vue.createElementVNode("view", { class: "section-title group-title" }, [
              vue.createElementVNode("text", { class: "section-name" }, "基本信息")
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "姓名"),
              vue.withDirectives(vue.createElementVNode(
                "input",
                {
                  "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.form.name = $event),
                  class: "field-input",
                  placeholder: "请输入姓名",
                  "placeholder-class": "ph"
                },
                null,
                512
                /* NEED_PATCH */
              ), [
                [vue.vModelText, $setup.form.name]
              ])
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "年龄"),
              vue.withDirectives(vue.createElementVNode(
                "input",
                {
                  "onUpdate:modelValue": _cache[3] || (_cache[3] = ($event) => $setup.form.age = $event),
                  type: "number",
                  class: "field-input",
                  placeholder: "请输入年龄",
                  "placeholder-class": "ph"
                },
                null,
                512
                /* NEED_PATCH */
              ), [
                [vue.vModelText, $setup.form.age]
              ])
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "性别"),
              vue.createElementVNode("view", { class: "gender-switch" }, [
                vue.createElementVNode(
                  "view",
                  {
                    class: vue.normalizeClass(["gender-item flex-center", { "gender-active": $setup.form.gender === "男" }]),
                    onClick: _cache[4] || (_cache[4] = ($event) => $setup.form.gender = "男")
                  },
                  "男",
                  2
                  /* CLASS */
                ),
                vue.createElementVNode(
                  "view",
                  {
                    class: vue.normalizeClass(["gender-item flex-center", { "gender-active": $setup.form.gender === "女" }]),
                    onClick: _cache[5] || (_cache[5] = ($event) => $setup.form.gender = "女")
                  },
                  "女",
                  2
                  /* CLASS */
                )
              ])
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "手机号"),
              vue.withDirectives(vue.createElementVNode(
                "input",
                {
                  "onUpdate:modelValue": _cache[6] || (_cache[6] = ($event) => $setup.form.phone = $event),
                  type: "text",
                  inputmode: "numeric",
                  class: "field-input",
                  placeholder: "请输入手机号",
                  "placeholder-class": "ph"
                },
                null,
                512
                /* NEED_PATCH */
              ), [
                [vue.vModelText, $setup.form.phone]
              ])
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "城市"),
              vue.withDirectives(vue.createElementVNode(
                "input",
                {
                  "onUpdate:modelValue": _cache[7] || (_cache[7] = ($event) => $setup.form.city = $event),
                  class: "field-input",
                  placeholder: "如 深圳·南山",
                  "placeholder-class": "ph"
                },
                null,
                512
                /* NEED_PATCH */
              ), [
                [vue.vModelText, $setup.form.city]
              ])
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "职业"),
              vue.withDirectives(vue.createElementVNode(
                "input",
                {
                  "onUpdate:modelValue": _cache[8] || (_cache[8] = ($event) => $setup.form.job = $event),
                  class: "field-input",
                  placeholder: "如 高级架构师",
                  "placeholder-class": "ph"
                },
                null,
                512
                /* NEED_PATCH */
              ), [
                [vue.vModelText, $setup.form.job]
              ])
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "学历"),
              vue.createElementVNode("view", {
                class: "flex",
                style: { "gap": "12rpx", "flex-wrap": "wrap" }
              }, [
                (vue.openBlock(), vue.createElementBlock(
                  vue.Fragment,
                  null,
                  vue.renderList($setup.EDUCATION_OPTS, (e) => {
                    return vue.createElementVNode("view", {
                      key: e,
                      class: vue.normalizeClass(["sel-pill flex-center", { "sel-active": $setup.form.education === e }]),
                      onClick: ($event) => $setup.form.education = e
                    }, vue.toDisplayString(e), 11, ["onClick"]);
                  }),
                  64
                  /* STABLE_FRAGMENT */
                ))
              ])
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "身高(cm)"),
              vue.withDirectives(vue.createElementVNode(
                "input",
                {
                  "onUpdate:modelValue": _cache[9] || (_cache[9] = ($event) => $setup.form.height = $event),
                  type: "number",
                  class: "field-input",
                  placeholder: "如 178",
                  "placeholder-class": "ph"
                },
                null,
                512
                /* NEED_PATCH */
              ), [
                [vue.vModelText, $setup.form.height]
              ])
            ]),
            vue.createElementVNode("view", { class: "field-row" }, [
              vue.createElementVNode("text", { class: "field-label" }, "年收入"),
              vue.withDirectives(vue.createElementVNode(
                "input",
                {
                  "onUpdate:modelValue": _cache[10] || (_cache[10] = ($event) => $setup.form.income = $event),
                  class: "field-input",
                  placeholder: "如 80-120万",
                  "placeholder-class": "ph"
                },
                null,
                512
                /* NEED_PATCH */
              ), [
                [vue.vModelText, $setup.form.income]
              ])
            ]),
            vue.createElementVNode("view", {
              class: "field-row",
              style: { "border-bottom": "none" }
            }, [
              vue.createElementVNode("text", { class: "field-label" }, "等级"),
              vue.createElementVNode("view", {
                class: "flex",
                style: { "gap": "12rpx", "flex-wrap": "wrap" }
              }, [
                (vue.openBlock(), vue.createElementBlock(
                  vue.Fragment,
                  null,
                  vue.renderList($setup.LEVEL_OPTS, (l) => {
                    return vue.createElementVNode("view", {
                      key: l,
                      class: vue.normalizeClass(["sel-pill flex-center", { "sel-active": $setup.form.level === l }]),
                      onClick: ($event) => $setup.form.level = l
                    }, vue.toDisplayString(l), 11, ["onClick"]);
                  }),
                  64
                  /* STABLE_FRAGMENT */
                ))
              ])
            ])
          ]),
          _: 1
          /* STABLE */
        }),
        vue.createVNode(_component_glass_card, {
          strong: "",
          padding: "36rpx",
          shadow: "normal",
          class: "group-card"
        }, {
          default: vue.withCtx(() => [
            vue.createElementVNode("view", { class: "section-title group-title" }, [
              vue.createElementVNode("text", { class: "section-name" }, "择偶要求")
            ]),
            vue.withDirectives(vue.createElementVNode(
              "textarea",
              {
                "onUpdate:modelValue": _cache[11] || (_cache[11] = ($event) => $setup.form.requirement = $event),
                class: "req-textarea",
                placeholder: "如：希望对方性格温和、有稳定事业，年龄相仿，重视家庭…",
                "placeholder-class": "ph",
                maxlength: 500
              },
              null,
              512
              /* NEED_PATCH */
            ), [
              [vue.vModelText, $setup.form.requirement]
            ])
          ]),
          _: 1
          /* STABLE */
        }),
        vue.createVNode(_component_glass_card, {
          strong: "",
          padding: "36rpx",
          shadow: "normal",
          class: "group-card"
        }, {
          default: vue.withCtx(() => [
            vue.createElementVNode("view", { class: "section-title group-title" }, [
              vue.createElementVNode("text", { class: "section-name" }, "自定义标签")
            ]),
            vue.createElementVNode("view", { class: "flex flex-wrap tag-cloud" }, [
              (vue.openBlock(true), vue.createElementBlock(
                vue.Fragment,
                null,
                vue.renderList($setup.customTags, (t) => {
                  return vue.openBlock(), vue.createElementBlock("view", {
                    key: t,
                    class: "custom-tag flex items-center"
                  }, [
                    vue.createElementVNode(
                      "text",
                      { class: "custom-tag-text" },
                      vue.toDisplayString(t),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode("view", {
                      class: "tag-close flex-center",
                      "hover-class": "hover-opacity",
                      onClick: ($event) => $setup.removeTag(t)
                    }, [
                      vue.createVNode(_component_app_icon, {
                        name: "close-ink",
                        size: "20rpx"
                      })
                    ], 8, ["onClick"])
                  ]);
                }),
                128
                /* KEYED_FRAGMENT */
              )),
              vue.withDirectives(vue.createElementVNode(
                "input",
                {
                  "onUpdate:modelValue": _cache[12] || (_cache[12] = ($event) => $setup.tagInput = $event),
                  class: "tag-input",
                  placeholder: "输入后回车生成标签",
                  "placeholder-class": "ph",
                  "confirm-type": "done",
                  onConfirm: $setup.addTag
                },
                null,
                544
                /* NEED_HYDRATION, NEED_PATCH */
              ), [
                [vue.vModelText, $setup.tagInput]
              ])
            ])
          ]),
          _: 1
          /* STABLE */
        }),
        vue.createElementVNode("view", { style: { "height": "220rpx" } })
      ]),
      vue.createElementVNode("view", { class: "bottom-bar" }, [
        vue.createElementVNode("view", {
          class: "btn-cancel flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.onCancel
        }, "取消"),
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["btn-save flex-center", { "is-saving": $setup.saving }]),
            "hover-class": "hover-scale",
            onClick: $setup.onSave
          },
          [
            !$setup.saving ? (vue.openBlock(), vue.createElementBlock(
              "text",
              { key: 0 },
              vue.toDisplayString($setup.isEdit ? "保存修改" : "确认录入"),
              1
              /* TEXT */
            )) : (vue.openBlock(), vue.createElementBlock("text", { key: 1 }, "保存中…"))
          ],
          2
          /* CLASS */
        )
      ]),
      $setup.panelOpen ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "panel-mask",
        onClick: _cache[13] || (_cache[13] = ($event) => $setup.panelOpen = false)
      })) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          class: vue.normalizeClass(["side-panel", { "panel-open": $setup.panelOpen }])
        },
        [
          vue.createElementVNode("view", { class: "panel-head" }, [
            vue.createElementVNode("text", { class: "panel-title" }, "AI 识别结果"),
            vue.createElementVNode(
              "text",
              { class: "panel-sub" },
              "请确认以下 " + vue.toDisplayString($setup.aiFields.length) + " 项信息",
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "panel-list" }, [
            (vue.openBlock(true), vue.createElementBlock(
              vue.Fragment,
              null,
              vue.renderList($setup.aiFields, (f) => {
                return vue.openBlock(), vue.createElementBlock("view", {
                  key: f.key,
                  class: "panel-item"
                }, [
                  vue.createElementVNode(
                    "text",
                    { class: "panel-label" },
                    vue.toDisplayString(f.label),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "panel-value" },
                    vue.toDisplayString(f.value),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    {
                      class: vue.normalizeClass(["panel-adopt", f.adopted ? "" : "panel-skip"])
                    },
                    vue.toDisplayString(f.adopted ? "已采用" : "未采用"),
                    3
                    /* TEXT, CLASS */
                  )
                ]);
              }),
              128
              /* KEYED_FRAGMENT */
            ))
          ]),
          vue.createElementVNode("view", {
            class: "panel-confirm flex-center",
            "hover-class": "hover-scale",
            onClick: _cache[14] || (_cache[14] = ($event) => $setup.panelOpen = false)
          }, " 确认无误 ")
        ],
        2
        /* CLASS */
      ),
      $setup.parseModal ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "panel-mask",
        onClick: _cache[15] || (_cache[15] = ($event) => $setup.parseModal = false)
      })) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          class: vue.normalizeClass(["parse-modal", { "panel-open": $setup.parseModal }])
        },
        [
          vue.createElementVNode("text", { class: "parse-title" }, "AI 智能建档"),
          vue.createElementVNode("text", { class: "parse-sub" }, "粘贴会员介绍文本，AI 将自动抽取姓名、年龄、职业等信息"),
          vue.withDirectives(vue.createElementVNode(
            "textarea",
            {
              "onUpdate:modelValue": _cache[16] || (_cache[16] = ($event) => $setup.parseText = $event),
              class: "parse-textarea",
              placeholder: "例如：张伟，32岁，男，上海工程师，硕士学历，身高178cm，喜欢摄影和徒步，手机号13812341234",
              "placeholder-class": "ph",
              maxlength: 500
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.parseText]
          ]),
          vue.createElementVNode(
            "view",
            {
              class: vue.normalizeClass(["parse-btn flex-center", { "is-saving": $setup.parsing }]),
              "hover-class": "hover-scale",
              onClick: $setup.doParse
            },
            vue.toDisplayString($setup.parsing ? "AI 解析中…" : "开始解析"),
            3
            /* TEXT, CLASS */
          )
        ],
        2
        /* CLASS */
      )
    ]);
  }
  const PagesMemberInput = /* @__PURE__ */ _export_sfc(_sfc_main$9, [["render", _sfc_render$8], ["__scopeId", "data-v-34fe3849"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/member/input.vue"]]);
  const _sfc_main$8 = /* @__PURE__ */ vue.defineComponent({
    __name: "detail",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const id = vue.ref("");
      const member = vue.ref(null);
      const pairs = vue.ref([]);
      const followups = vue.ref([]);
      const summary = vue.ref(null);
      const loading = vue.ref(false);
      const fetchAll = async () => {
        loading.value = true;
        try {
          const r = await api.memberDetail(id.value);
          member.value = r.member;
          pairs.value = r.pairs || [];
          followups.value = r.followups || [];
          try {
            summary.value = await api.followupSummary(id.value);
          } catch {
            summary.value = null;
          }
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          loading.value = false;
        }
      };
      onLoad((q) => {
        id.value = String((q == null ? void 0 : q.id) || "");
        if (id.value)
          fetchAll();
      });
      const tagList = (tags) => tags ? tags.split(/[,，、]/).filter(Boolean) : [];
      const outcomeCls = (o) => o === "成交意向" ? "oc-good" : o === "无意向" ? "oc-bad" : "oc-mid";
      const infoGrid = () => {
        const m = member.value;
        if (!m)
          return [];
        return [
          { label: "身高", value: m.height ? `${m.height}cm` : "—" },
          { label: "手机", value: m.phone ? maskPhone(m.phone) : "—" },
          { label: "学历", value: m.education || "—" },
          { label: "籍贯", value: m.hometown || "—" },
          { label: "现居", value: m.city || "—" },
          { label: "婚姻", value: m.marriage || "—" },
          { label: "年收入", value: m.income || "—" },
          { label: "子女", value: m.hasChild || "—" },
          { label: "房产", value: m.house || "—" },
          { label: "座驾", value: m.car || "—" },
          { label: "等级", value: m.level || "—" }
        ];
      };
      const addMode = vue.ref(false);
      const fuType = vue.ref("电话");
      const fuContent = vue.ref("");
      const fuOutcome = vue.ref("待定");
      const fuNext = vue.ref("");
      const fuSaving = vue.ref(false);
      const FU_TYPES = ["电话", "微信", "约见", "回访", "系统"];
      const FU_OUTCOMES = ["待定", "成交意向", "无意向"];
      const today = () => {
        const d = /* @__PURE__ */ new Date();
        const p = (n2) => n2 < 10 ? "0" + n2 : "" + n2;
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
      };
      const in3days = () => {
        const d = /* @__PURE__ */ new Date();
        d.setDate(d.getDate() + 3);
        const p = (n2) => n2 < 10 ? "0" + n2 : "" + n2;
        return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
      };
      const submitFollowup = async () => {
        if (!fuContent.value.trim()) {
          uni.showToast({ title: "请填写沟通要点", icon: "none" });
          return;
        }
        fuSaving.value = true;
        try {
          await api.createFollowup(id.value, {
            type: fuType.value,
            content: fuContent.value.trim(),
            outcome: fuOutcome.value,
            nextFollowUp: fuNext.value || void 0
          });
          fuContent.value = "";
          fuNext.value = "";
          addMode.value = false;
          uni.showToast({ title: "跟进已记录", icon: "success" });
          fetchAll();
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          fuSaving.value = false;
        }
      };
      const goBack = () => uni.navigateBack();
      const onEdit = () => uni.navigateTo({ url: `/pages/member/input?id=${id.value}` });
      const onSchedule = () => uni.navigateTo({ url: "/pages/schedule/index" });
      const onMatch = () => uni.navigateTo({ url: `/pages/match/index?memberId=${id.value}` });
      const onMessage = async () => {
        try {
          const c = await api.createConversation(id.value);
          uni.navigateTo({ url: `/pages/message/detail?id=${c.id}` });
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        }
      };
      const onPairTap = (p) => uni.navigateTo({ url: `/pages/pair/detail?id=${p.id}` });
      const __returned__ = { statusBarHeight, id, member, pairs, followups, summary, loading, fetchAll, tagList, outcomeCls, infoGrid, addMode, fuType, fuContent, fuOutcome, fuNext, fuSaving, FU_TYPES, FU_OUTCOMES, today, in3days, submitFollowup, goBack, onEdit, onSchedule, onMatch, onMessage, onPairTap };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$7(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_loading_red_line = resolveEasycom(vue.resolveDynamicComponent("loading-red-line"), __easycom_1);
    const _component_app_icon = resolveEasycom(vue.resolveDynamicComponent("app-icon"), __easycom_2$1);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      $setup.loading ? (vue.openBlock(), vue.createBlock(_component_loading_red_line, {
        key: 0,
        top: $setup.statusBarHeight + "px"
      }, null, 8, ["top"])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      vue.createElementVNode("view", { class: "glass-bar topbar" }, [
        vue.createElementVNode("view", {
          class: "circle-btn flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.goBack
        }, [
          vue.createVNode(_component_app_icon, {
            name: "back-ink",
            size: "44rpx"
          })
        ]),
        vue.createElementVNode("text", { class: "topbar-title" }, "会员资料"),
        vue.createElementVNode("view", {
          class: "circle-btn flex-center",
          "hover-class": "hover-opacity",
          style: { "visibility": "hidden" }
        }, [
          vue.createVNode(_component_app_icon, {
            name: "more-ink",
            size: "36rpx"
          })
        ])
      ]),
      $setup.member ? (vue.openBlock(), vue.createElementBlock(
        vue.Fragment,
        { key: 1 },
        [
          vue.createElementVNode("view", { class: "hero" }, [
            vue.createElementVNode("view", { class: "avatar-wrap" }, [
              vue.createElementVNode(
                "view",
                {
                  class: "hero-avatar flex-center",
                  style: vue.normalizeStyle({ background: $setup.member.avatarBg })
                },
                vue.toDisplayString($setup.member.name[0]),
                5
                /* TEXT, STYLE */
              ),
              $setup.member.verified ? (vue.openBlock(), vue.createElementBlock("view", {
                key: 0,
                class: "badge-gold flex-center"
              }, [
                vue.createVNode(_component_app_icon, {
                  name: "check-white",
                  size: "24rpx"
                })
              ])) : vue.createCommentVNode("v-if", true)
            ]),
            vue.createElementVNode("view", {
              class: "flex items-baseline justify-center",
              style: { "gap": "12rpx", "margin-top": "24rpx" }
            }, [
              vue.createElementVNode(
                "text",
                { class: "hero-name" },
                vue.toDisplayString($setup.member.name),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "hero-gender" },
                vue.toDisplayString($setup.member.gender === "女" ? "♀" : "♂"),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "text",
                { class: "hero-age" },
                "· " + vue.toDisplayString($setup.member.age) + "岁",
                1
                /* TEXT */
              )
            ]),
            vue.createElementVNode(
              "text",
              { class: "hero-sub" },
              vue.toDisplayString($setup.member.city || $setup.member.level) + " · " + vue.toDisplayString($setup.member.job || "—"),
              1
              /* TEXT */
            ),
            vue.createElementVNode("view", {
              class: "flex justify-center",
              style: { "gap": "16rpx", "margin-top": "20rpx" }
            }, [
              vue.createElementVNode("view", { class: "cert-pill cert-gold flex items-center" }, [
                vue.createVNode(_component_app_icon, {
                  name: "star-gold",
                  size: "24rpx"
                }),
                vue.createElementVNode(
                  "text",
                  null,
                  vue.toDisplayString($setup.member.level || "普通"),
                  1
                  /* TEXT */
                )
              ]),
              vue.createElementVNode(
                "view",
                {
                  class: vue.normalizeClass(["cert-pill", $setup.member.status === "已认证" ? "cert-green" : "cert-primary"])
                },
                [
                  vue.createElementVNode(
                    "text",
                    null,
                    vue.toDisplayString($setup.member.status || "待审核"),
                    1
                    /* TEXT */
                  )
                ],
                2
                /* CLASS */
              ),
              $setup.member.agent ? (vue.openBlock(), vue.createElementBlock("view", {
                key: 0,
                class: "cert-pill cert-primary"
              }, [
                vue.createElementVNode(
                  "text",
                  null,
                  "红娘：" + vue.toDisplayString($setup.member.agent),
                  1
                  /* TEXT */
                )
              ])) : vue.createCommentVNode("v-if", true)
            ]),
            vue.createElementVNode("view", { class: "score-pill flex items-center justify-center" }, [
              vue.createVNode(_component_app_icon, {
                name: "handshake-white",
                size: "28rpx"
              }),
              vue.createElementVNode("text", { class: "score-label" }, "牵线"),
              vue.createElementVNode(
                "text",
                { class: "score-num" },
                vue.toDisplayString($setup.member.matchCount ?? 0),
                1
                /* TEXT */
              ),
              vue.createElementVNode("text", { class: "score-label" }, "次 · 成功率"),
              vue.createElementVNode(
                "text",
                { class: "score-num" },
                vue.toDisplayString($setup.member.successRate ?? 0) + "%",
                1
                /* TEXT */
              )
            ])
          ]),
          vue.createElementVNode("view", { class: "quick-row flex" }, [
            vue.createElementVNode("view", {
              class: "q-btn q-msg flex-center",
              "hover-class": "hover-opacity",
              onClick: $setup.onMessage
            }, [
              vue.createVNode(_component_app_icon, {
                name: "message-primary",
                size: "32rpx"
              }),
              vue.createElementVNode("text", null, "发消息")
            ]),
            vue.createElementVNode("view", {
              class: "q-btn q-match flex-center",
              "hover-class": "hover-opacity",
              onClick: $setup.onMatch
            }, [
              vue.createVNode(_component_app_icon, {
                name: "handshake-white",
                size: "32rpx"
              }),
              vue.createElementVNode("text", null, "发起牵线")
            ]),
            vue.createElementVNode("view", {
              class: "q-btn q-edit flex-center",
              "hover-class": "hover-opacity",
              onClick: $setup.onEdit
            }, [
              vue.createVNode(_component_app_icon, {
                name: "edit-primary",
                size: "32rpx"
              }),
              vue.createElementVNode("text", null, "编辑")
            ])
          ]),
          vue.createElementVNode("view", { class: "cards" }, [
            vue.createVNode(_component_glass_card, {
              strong: "",
              padding: "36rpx",
              radius: "40rpx"
            }, {
              default: vue.withCtx(() => [
                vue.createElementVNode("view", { class: "section-title card-title" }, [
                  vue.createElementVNode("text", { class: "card-title-text" }, "基本信息")
                ]),
                vue.createElementVNode("view", { class: "info-grid" }, [
                  (vue.openBlock(true), vue.createElementBlock(
                    vue.Fragment,
                    null,
                    vue.renderList($setup.infoGrid(), (g) => {
                      return vue.openBlock(), vue.createElementBlock("view", {
                        key: g.label
                      }, [
                        vue.createElementVNode(
                          "text",
                          { class: "ig-label" },
                          vue.toDisplayString(g.label),
                          1
                          /* TEXT */
                        ),
                        vue.createElementVNode(
                          "text",
                          { class: "ig-value" },
                          vue.toDisplayString(g.value),
                          1
                          /* TEXT */
                        )
                      ]);
                    }),
                    128
                    /* KEYED_FRAGMENT */
                  ))
                ])
              ]),
              _: 1
              /* STABLE */
            }),
            vue.createVNode(_component_glass_card, {
              strong: "",
              padding: "36rpx",
              radius: "40rpx"
            }, {
              default: vue.withCtx(() => [
                vue.createElementVNode("view", { class: "section-title card-title" }, [
                  vue.createElementVNode("text", { class: "card-title-text" }, "择偶要求")
                ]),
                vue.createElementVNode(
                  "text",
                  { class: "req-text" },
                  vue.toDisplayString($setup.member.requirement || "未填写择偶要求"),
                  1
                  /* TEXT */
                )
              ]),
              _: 1
              /* STABLE */
            }),
            $setup.member.bio || $setup.member.tags ? (vue.openBlock(), vue.createBlock(_component_glass_card, {
              key: 0,
              strong: "",
              padding: "36rpx",
              radius: "40rpx"
            }, {
              default: vue.withCtx(() => [
                vue.createElementVNode("view", { class: "section-title card-title" }, [
                  vue.createElementVNode("text", { class: "card-title-text" }, "自我介绍")
                ]),
                vue.createElementVNode(
                  "text",
                  { class: "intro-text" },
                  vue.toDisplayString($setup.member.bio || "暂未填写自我介绍"),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("view", {
                  class: "flex flex-wrap",
                  style: { "gap": "14rpx", "margin-top": "20rpx" }
                }, [
                  (vue.openBlock(true), vue.createElementBlock(
                    vue.Fragment,
                    null,
                    vue.renderList($setup.tagList($setup.member.tags), (h) => {
                      return vue.openBlock(), vue.createElementBlock("view", {
                        key: h,
                        class: "hobby-tag flex items-center"
                      }, [
                        vue.createVNode(_component_app_icon, {
                          name: "act-heart",
                          size: "22rpx"
                        }),
                        vue.createElementVNode(
                          "text",
                          null,
                          vue.toDisplayString(h),
                          1
                          /* TEXT */
                        )
                      ]);
                    }),
                    128
                    /* KEYED_FRAGMENT */
                  ))
                ])
              ]),
              _: 1
              /* STABLE */
            })) : vue.createCommentVNode("v-if", true),
            $setup.summary ? (vue.openBlock(), vue.createBlock(_component_glass_card, {
              key: 1,
              strong: "",
              padding: "36rpx",
              radius: "40rpx"
            }, {
              default: vue.withCtx(() => [
                vue.createElementVNode("view", { class: "section-title card-title" }, [
                  vue.createElementVNode("text", { class: "card-title-text" }, "AI 跟进摘要")
                ]),
                vue.createElementVNode(
                  "text",
                  { class: "intro-text" },
                  vue.toDisplayString($setup.summary.summary),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode("view", {
                  class: "flex",
                  style: { "gap": "16rpx", "margin-top": "20rpx", "flex-wrap": "wrap" }
                }, [
                  vue.createElementVNode("view", { class: "mini-stat" }, [
                    vue.createElementVNode("text", { class: "mini-label" }, "意向"),
                    vue.createElementVNode(
                      "text",
                      { class: "mini-value" },
                      vue.toDisplayString($setup.summary.intention),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "mini-stat" }, [
                    vue.createElementVNode("text", { class: "mini-label" }, "跟进次数"),
                    vue.createElementVNode(
                      "text",
                      { class: "mini-value" },
                      vue.toDisplayString($setup.summary.count),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                vue.createElementVNode("view", { class: "suggest-box" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "suggest-text" },
                    "💡 " + vue.toDisplayString($setup.summary.suggestion),
                    1
                    /* TEXT */
                  )
                ]),
                (vue.openBlock(true), vue.createElementBlock(
                  vue.Fragment,
                  null,
                  vue.renderList($setup.summary.reminders, (r, i) => {
                    return vue.openBlock(), vue.createElementBlock(
                      "text",
                      {
                        key: i,
                        class: "remind-text"
                      },
                      "· " + vue.toDisplayString(r),
                      1
                      /* TEXT */
                    );
                  }),
                  128
                  /* KEYED_FRAGMENT */
                ))
              ]),
              _: 1
              /* STABLE */
            })) : vue.createCommentVNode("v-if", true),
            vue.createVNode(_component_glass_card, {
              strong: "",
              padding: "36rpx",
              radius: "40rpx"
            }, {
              default: vue.withCtx(() => [
                vue.createElementVNode("view", { class: "flex-between" }, [
                  vue.createElementVNode("view", { class: "section-title" }, [
                    vue.createElementVNode("text", { class: "card-title-text" }, "跟进时间线")
                  ]),
                  vue.createElementVNode("view", {
                    class: "add-btn flex-center",
                    "hover-class": "hover-opacity",
                    onClick: _cache[0] || (_cache[0] = ($event) => $setup.addMode = !$setup.addMode)
                  }, [
                    vue.createVNode(_component_app_icon, {
                      name: $setup.addMode ? "minus-primary" : "plus-primary",
                      size: "28rpx"
                    }, null, 8, ["name"]),
                    vue.createElementVNode(
                      "text",
                      null,
                      vue.toDisplayString($setup.addMode ? "收起" : "记录跟进"),
                      1
                      /* TEXT */
                    )
                  ])
                ]),
                $setup.addMode ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 0,
                  class: "fu-form"
                }, [
                  vue.createElementVNode("view", { class: "fu-row" }, [
                    vue.createElementVNode("text", { class: "fu-label" }, "方式"),
                    vue.createElementVNode("view", {
                      class: "flex",
                      style: { "gap": "12rpx", "flex-wrap": "wrap" }
                    }, [
                      (vue.openBlock(), vue.createElementBlock(
                        vue.Fragment,
                        null,
                        vue.renderList($setup.FU_TYPES, (t) => {
                          return vue.createElementVNode("view", {
                            key: t,
                            class: vue.normalizeClass(["sel-pill flex-center", { "sel-active": $setup.fuType === t }]),
                            onClick: ($event) => $setup.fuType = t
                          }, vue.toDisplayString(t), 11, ["onClick"]);
                        }),
                        64
                        /* STABLE_FRAGMENT */
                      ))
                    ])
                  ]),
                  vue.createElementVNode("view", { class: "fu-row" }, [
                    vue.createElementVNode("text", { class: "fu-label" }, "要点"),
                    vue.withDirectives(vue.createElementVNode(
                      "textarea",
                      {
                        "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.fuContent = $event),
                        class: "fu-textarea",
                        placeholder: "记录本次沟通内容…",
                        "placeholder-class": "ph",
                        maxlength: 1e3
                      },
                      null,
                      512
                      /* NEED_PATCH */
                    ), [
                      [vue.vModelText, $setup.fuContent]
                    ])
                  ]),
                  vue.createElementVNode("view", { class: "fu-row" }, [
                    vue.createElementVNode("text", { class: "fu-label" }, "意向"),
                    vue.createElementVNode("view", {
                      class: "flex",
                      style: { "gap": "12rpx", "flex-wrap": "wrap" }
                    }, [
                      (vue.openBlock(), vue.createElementBlock(
                        vue.Fragment,
                        null,
                        vue.renderList($setup.FU_OUTCOMES, (o) => {
                          return vue.createElementVNode("view", {
                            key: o,
                            class: vue.normalizeClass(["sel-pill flex-center", { "sel-active": $setup.fuOutcome === o }]),
                            onClick: ($event) => $setup.fuOutcome = o
                          }, vue.toDisplayString(o), 11, ["onClick"]);
                        }),
                        64
                        /* STABLE_FRAGMENT */
                      ))
                    ])
                  ]),
                  vue.createElementVNode("view", { class: "fu-row" }, [
                    vue.createElementVNode("text", { class: "fu-label" }, "下次"),
                    vue.createElementVNode("view", {
                      class: "flex",
                      style: { "gap": "12rpx" }
                    }, [
                      vue.withDirectives(vue.createElementVNode("input", {
                        "onUpdate:modelValue": _cache[2] || (_cache[2] = ($event) => $setup.fuNext = $event),
                        class: "fu-input",
                        placeholder: "yyyy-MM-dd",
                        style: { "flex": "1" }
                      }, null, 8, ["placeholder"]), [
                        [vue.vModelText, $setup.fuNext]
                      ]),
                      vue.createElementVNode("view", {
                        class: "fill-btn flex-center",
                        onClick: _cache[3] || (_cache[3] = ($event) => $setup.fuNext = $setup.in3days())
                      }, "+3天")
                    ])
                  ]),
                  vue.createElementVNode(
                    "view",
                    {
                      class: vue.normalizeClass(["submit-btn flex-center", { disabled: $setup.fuSaving }]),
                      onClick: $setup.submitFollowup
                    },
                    vue.toDisplayString($setup.fuSaving ? "保存中…" : "保存跟进"),
                    3
                    /* TEXT, CLASS */
                  )
                ])) : vue.createCommentVNode("v-if", true),
                $setup.followups.length ? (vue.openBlock(), vue.createElementBlock("view", {
                  key: 1,
                  class: "timeline"
                }, [
                  (vue.openBlock(true), vue.createElementBlock(
                    vue.Fragment,
                    null,
                    vue.renderList($setup.followups, (f) => {
                      return vue.openBlock(), vue.createElementBlock("view", {
                        key: f.id,
                        class: "tl-item"
                      }, [
                        vue.createElementVNode("view", { class: "tl-dot" }),
                        vue.createElementVNode("view", { class: "tl-body" }, [
                          vue.createElementVNode("view", { class: "flex-between" }, [
                            vue.createElementVNode("view", {
                              class: "flex",
                              style: { "gap": "12rpx", "align-items": "center" }
                            }, [
                              vue.createElementVNode(
                                "text",
                                { class: "tl-type" },
                                vue.toDisplayString(f.type),
                                1
                                /* TEXT */
                              ),
                              vue.createElementVNode(
                                "text",
                                {
                                  class: vue.normalizeClass(["tl-outcome", $setup.outcomeCls(f.outcome)])
                                },
                                vue.toDisplayString(f.outcome),
                                3
                                /* TEXT, CLASS */
                              )
                            ]),
                            vue.createElementVNode(
                              "text",
                              { class: "tl-time" },
                              vue.toDisplayString(f.timeAgo || f.createdAt),
                              1
                              /* TEXT */
                            )
                          ]),
                          vue.createElementVNode(
                            "text",
                            { class: "tl-content" },
                            vue.toDisplayString(f.content),
                            1
                            /* TEXT */
                          ),
                          f.nextFollowUp ? (vue.openBlock(), vue.createElementBlock(
                            "text",
                            {
                              key: 0,
                              class: "tl-next"
                            },
                            "下次跟进：" + vue.toDisplayString(f.nextFollowUp),
                            1
                            /* TEXT */
                          )) : vue.createCommentVNode("v-if", true)
                        ])
                      ]);
                    }),
                    128
                    /* KEYED_FRAGMENT */
                  ))
                ])) : (vue.openBlock(), vue.createElementBlock("view", {
                  key: 2,
                  class: "empty-fu"
                }, [
                  vue.createElementVNode("text", null, "暂无跟进记录，点击右上角「记录跟进」开始留痕")
                ]))
              ]),
              _: 1
              /* STABLE */
            }),
            $setup.pairs.length ? (vue.openBlock(), vue.createBlock(_component_glass_card, {
              key: 2,
              strong: "",
              padding: "36rpx",
              radius: "40rpx"
            }, {
              default: vue.withCtx(() => [
                vue.createElementVNode("view", { class: "section-title card-title" }, [
                  vue.createElementVNode("text", { class: "card-title-text" }, "牵线记录")
                ]),
                vue.createElementVNode("view", { class: "pair-list" }, [
                  (vue.openBlock(true), vue.createElementBlock(
                    vue.Fragment,
                    null,
                    vue.renderList($setup.pairs, (p) => {
                      return vue.openBlock(), vue.createElementBlock("view", {
                        key: p.id,
                        class: "pair-row",
                        "hover-class": "hover-opacity",
                        onClick: ($event) => $setup.onPairTap(p)
                      }, [
                        vue.createElementVNode("view", {
                          class: "flex items-center",
                          style: { "gap": "16rpx", "flex": "1", "min-width": "0" }
                        }, [
                          vue.createElementVNode(
                            "text",
                            { class: "pair-name" },
                            vue.toDisplayString(p.maleName),
                            1
                            /* TEXT */
                          ),
                          vue.createElementVNode("text", { class: "pair-x" }, "×"),
                          vue.createElementVNode(
                            "text",
                            { class: "pair-name" },
                            vue.toDisplayString(p.femaleName),
                            1
                            /* TEXT */
                          )
                        ]),
                        vue.createElementVNode("view", {
                          class: "flex items-center",
                          style: { "gap": "12rpx" }
                        }, [
                          vue.createElementVNode(
                            "text",
                            { class: "pair-score" },
                            vue.toDisplayString(p.score) + "分",
                            1
                            /* TEXT */
                          ),
                          vue.createElementVNode(
                            "view",
                            {
                              class: vue.normalizeClass(["pair-status", "ps-" + p.status])
                            },
                            vue.toDisplayString(p.status || p.stage),
                            3
                            /* TEXT, CLASS */
                          )
                        ])
                      ], 8, ["onClick"]);
                    }),
                    128
                    /* KEYED_FRAGMENT */
                  ))
                ])
              ]),
              _: 1
              /* STABLE */
            })) : vue.createCommentVNode("v-if", true)
          ])
        ],
        64
        /* STABLE_FRAGMENT */
      )) : !$setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 2,
        class: "empty-state flex-center"
      }, [
        vue.createElementVNode("view", { class: "empty-icon flex-center" }, [
          vue.createVNode(_component_app_icon, {
            name: "search-ink",
            size: "56rpx"
          })
        ]),
        vue.createElementVNode("text", { class: "empty-title" }, "未找到会员资料"),
        vue.createElementVNode("text", { class: "empty-sub" }, "该会员可能已不存在，或网络异常"),
        vue.createElementVNode("view", {
          class: "empty-btn flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.goBack
        }, "返回上一页")
      ])) : vue.createCommentVNode("v-if", true),
      $setup.member ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 3,
        style: { "height": "60rpx" }
      })) : vue.createCommentVNode("v-if", true)
    ]);
  }
  const PagesMemberDetail = /* @__PURE__ */ _export_sfc(_sfc_main$8, [["render", _sfc_render$7], ["__scopeId", "data-v-77570c0b"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/member/detail.vue"]]);
  const _sfc_main$7 = /* @__PURE__ */ vue.defineComponent({
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const pad = (n2) => n2 < 10 ? "0" + n2 : "" + n2;
      const dateStr = (d) => `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
      const isSameDay = (a, b) => a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
      const WEEK_LABELS = ["一", "二", "三", "四", "五", "六", "日"];
      const weekDays = vue.ref([]);
      const buildWeek = () => {
        const now = /* @__PURE__ */ new Date();
        const dow = now.getDay();
        const diffToMon = dow === 0 ? -6 : 1 - dow;
        const mon = new Date(now);
        mon.setDate(now.getDate() + diffToMon);
        const arr = [];
        for (let i = 0; i < 7; i++) {
          const d = new Date(mon);
          d.setDate(mon.getDate() + i);
          const wd = d.getDay();
          arr.push({
            label: WEEK_LABELS[wd === 0 ? 6 : wd - 1],
            date: d.getDate(),
            dateStr: dateStr(d),
            weekend: wd === 0 || wd === 6,
            today: isSameDay(d, now),
            dot: false
          });
        }
        weekDays.value = arr;
      };
      const filters = ["全部", "待确认", "已完成"];
      const activeFilter = vue.ref("全部");
      const meetings = vue.ref([]);
      const loading = vue.ref(false);
      const fetchList = async () => {
        var _a;
        loading.value = true;
        try {
          const events = await api.calendarEvents();
          meetings.value = (events || []).map((e) => ({
            id: String(e.id),
            date: e.date || "",
            time: e.startTime || e.time || "",
            place: e.place || "",
            status: e.status || "待确认",
            title: e.title || "约见",
            note: e.note || e.remark || "",
            highlight: !!e.highlight
          }));
        } catch {
          uni.showToast({ title: "后端未连接", icon: "none" });
        } finally {
          loading.value = false;
          (_a = uni.stopPullDownRefresh) == null ? void 0 : _a.call(uni);
        }
      };
      const refreshDots = () => {
        const counts = {};
        for (const m of meetings.value)
          counts[m.date] = (counts[m.date] || 0) + 1;
        weekDays.value = weekDays.value.map((d) => ({ ...d, dot: (counts[d.dateStr] || 0) > 0 }));
      };
      onShow(async () => {
        buildWeek();
        await fetchList();
        refreshDots();
      });
      onPullDownRefresh(fetchList);
      const isDone = (s2) => s2.includes("完成") || s2.includes("已确认");
      const displayList = vue.computed(
        () => meetings.value.filter(
          (m) => activeFilter.value === "全部" ? true : activeFilter.value === "已完成" ? isDone(m.status) : !isDone(m.status)
        ).slice().sort((a, b) => `${a.date} ${a.time}`.localeCompare(`${b.date} ${b.time}`))
      );
      const grouped = vue.computed(() => {
        const map = /* @__PURE__ */ new Map();
        for (const m of displayList.value) {
          const key = m.date || "未排期";
          if (!map.has(key))
            map.set(key, []);
          map.get(key).push(m);
        }
        return [...map.entries()].map(([date, list]) => ({ date, list }));
      });
      const todayCount = vue.computed(() => meetings.value.filter((m) => m.date === dateStr(/* @__PURE__ */ new Date())).length);
      const confirmedCount = vue.computed(() => meetings.value.filter((m) => isDone(m.status)).length);
      const pendingCount = vue.computed(() => meetings.value.filter((m) => !isDone(m.status)).length);
      const nextMeeting = vue.computed(() => displayList.value[0]);
      const dotClass = (s2) => isDone(s2) ? "dot-success" : "dot-warning";
      const pillClass = (s2) => isDone(s2) ? "pill-success" : "pill-warning";
      const onDetail = (m) => uni.showToast({ title: `${m.time} ${m.place}`, icon: "none" });
      const onContact = (m) => uni.showToast({ title: `联系：${m.title}`, icon: "none" });
      const goBack = () => uni.navigateBack();
      const onNew = () => uni.showToast({ title: "新建约见（开发中）", icon: "none" });
      const __returned__ = { statusBarHeight, pad, dateStr, isSameDay, WEEK_LABELS, weekDays, buildWeek, filters, activeFilter, meetings, loading, fetchList, refreshDots, isDone, displayList, grouped, todayCount, confirmedCount, pendingCount, nextMeeting, dotClass, pillClass, onDetail, onContact, goBack, onNew };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$6(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_loading_red_line = resolveEasycom(vue.resolveDynamicComponent("loading-red-line"), __easycom_1);
    const _component_app_icon = resolveEasycom(vue.resolveDynamicComponent("app-icon"), __easycom_2$1);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      $setup.loading ? (vue.openBlock(), vue.createBlock(_component_loading_red_line, {
        key: 0,
        top: $setup.statusBarHeight + "px"
      }, null, 8, ["top"])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      vue.createElementVNode("view", { class: "glass-bar topbar" }, [
        vue.createElementVNode("view", {
          class: "circle-btn flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.goBack
        }, [
          vue.createVNode(_component_app_icon, {
            name: "back-ink",
            size: "40rpx"
          })
        ]),
        vue.createElementVNode("text", { class: "topbar-title" }, "约见日程"),
        vue.createElementVNode("view", {
          class: "new-btn flex-center",
          "hover-class": "hover-opacity",
          onClick: $setup.onNew
        }, [
          vue.createVNode(_component_app_icon, {
            name: "calendar-plus-primary",
            size: "40rpx"
          })
        ])
      ]),
      vue.createElementVNode("view", { class: "overview-card flex-between" }, [
        vue.createElementVNode("view", { class: "ov-left" }, [
          vue.createElementVNode("text", { class: "ov-label" }, "今日约见"),
          vue.createElementVNode("view", {
            class: "flex items-baseline",
            style: { "gap": "8rpx", "margin": "8rpx 0" }
          }, [
            vue.createElementVNode(
              "text",
              { class: "ov-num" },
              vue.toDisplayString($setup.todayCount),
              1
              /* TEXT */
            ),
            vue.createElementVNode("text", { class: "ov-unit" }, "场")
          ]),
          $setup.nextMeeting ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 0,
            class: "flex items-center",
            style: { "gap": "8rpx" }
          }, [
            vue.createVNode(_component_app_icon, {
              name: "clock-ink",
              size: "24rpx"
            }),
            vue.createElementVNode(
              "text",
              { class: "ov-next" },
              "下一场: " + vue.toDisplayString($setup.nextMeeting.time) + " " + vue.toDisplayString($setup.nextMeeting.place),
              1
              /* TEXT */
            )
          ])) : vue.createCommentVNode("v-if", true)
        ]),
        vue.createElementVNode("view", { class: "ov-pills" }, [
          vue.createElementVNode("view", { class: "ov-pill flex items-center op-success" }, [
            vue.createVNode(_component_app_icon, {
              name: "check-circle-success",
              size: "22rpx"
            }),
            vue.createElementVNode(
              "text",
              null,
              vue.toDisplayString($setup.confirmedCount) + " 已确认",
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "ov-pill flex items-center op-warning" }, [
            vue.createVNode(_component_app_icon, {
              name: "clock-warning",
              size: "22rpx"
            }),
            vue.createElementVNode(
              "text",
              null,
              vue.toDisplayString($setup.pendingCount) + " 待确认",
              1
              /* TEXT */
            )
          ])
        ])
      ]),
      vue.createVNode(_component_glass_card, {
        padding: "28rpx 24rpx",
        radius: "40rpx",
        class: "week-card"
      }, {
        default: vue.withCtx(() => [
          vue.createElementVNode("view", { class: "flex justify-around" }, [
            (vue.openBlock(true), vue.createElementBlock(
              vue.Fragment,
              null,
              vue.renderList($setup.weekDays, (d) => {
                return vue.openBlock(), vue.createElementBlock(
                  "view",
                  {
                    key: d.date,
                    class: vue.normalizeClass(["day-col flex-center", { "day-today": d.today }])
                  },
                  [
                    vue.createElementVNode(
                      "text",
                      {
                        class: vue.normalizeClass(["day-label", { "day-weekend": d.weekend }])
                      },
                      vue.toDisplayString(d.label),
                      3
                      /* TEXT, CLASS */
                    ),
                    vue.createElementVNode(
                      "view",
                      {
                        class: vue.normalizeClass(["day-date flex-center", { "date-today": d.today }])
                      },
                      vue.toDisplayString(d.date),
                      3
                      /* TEXT, CLASS */
                    ),
                    vue.createElementVNode(
                      "view",
                      {
                        class: vue.normalizeClass(["day-dot", { "dot-on": d.dot, "dot-today": d.today }])
                      },
                      null,
                      2
                      /* CLASS */
                    )
                  ],
                  2
                  /* CLASS */
                );
              }),
              128
              /* KEYED_FRAGMENT */
            ))
          ])
        ]),
        _: 1
        /* STABLE */
      }),
      vue.createElementVNode("view", {
        class: "flex",
        style: { "gap": "16rpx", "margin": "32rpx 40rpx 0" }
      }, [
        (vue.openBlock(), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.filters, (f) => {
            return vue.createElementVNode("view", {
              key: f,
              class: vue.normalizeClass(["filter-pill flex-center", $setup.activeFilter === f ? f === "全部" ? "fp-active" : f === "待确认" ? "fp-warning" : "fp-success" : ""]),
              onClick: ($event) => $setup.activeFilter = f
            }, vue.toDisplayString(f), 11, ["onClick"]);
          }),
          64
          /* STABLE_FRAGMENT */
        ))
      ]),
      vue.createElementVNode("view", { class: "timeline" }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.grouped, (g) => {
            return vue.openBlock(), vue.createElementBlock(
              vue.Fragment,
              {
                key: g.date
              },
              [
                vue.createElementVNode("view", { class: "day-divider flex items-center" }, [
                  vue.createElementVNode(
                    "text",
                    { class: "dd-text" },
                    vue.toDisplayString(g.date),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode("view", { class: "dd-line" })
                ]),
                (vue.openBlock(true), vue.createElementBlock(
                  vue.Fragment,
                  null,
                  vue.renderList(g.list, (m, i) => {
                    return vue.openBlock(), vue.createElementBlock("view", {
                      key: m.id,
                      class: "meeting-item flex"
                    }, [
                      vue.createElementVNode("view", { class: "t-rail" }, [
                        vue.createElementVNode(
                          "text",
                          { class: "t-time" },
                          vue.toDisplayString(m.time),
                          1
                          /* TEXT */
                        ),
                        vue.createElementVNode(
                          "view",
                          {
                            class: vue.normalizeClass(["t-dot", $setup.dotClass(m.status)])
                          },
                          null,
                          2
                          /* CLASS */
                        ),
                        i < g.list.length - 1 ? (vue.openBlock(), vue.createElementBlock("view", {
                          key: 0,
                          class: "t-line"
                        })) : vue.createCommentVNode("v-if", true)
                      ]),
                      vue.createVNode(
                        _component_glass_card,
                        {
                          strong: "",
                          padding: "28rpx 32rpx",
                          radius: "36rpx",
                          class: "m-card"
                        },
                        {
                          default: vue.withCtx(() => [
                            vue.createElementVNode("view", { class: "flex-between items-center" }, [
                              vue.createElementVNode(
                                "text",
                                { class: "m-title" },
                                vue.toDisplayString(m.title),
                                1
                                /* TEXT */
                              ),
                              vue.createElementVNode(
                                "text",
                                {
                                  class: vue.normalizeClass(["m-pill", $setup.pillClass(m.status)])
                                },
                                vue.toDisplayString(m.status),
                                3
                                /* TEXT, CLASS */
                              )
                            ]),
                            vue.createElementVNode("view", { class: "flex items-center m-row" }, [
                              vue.createVNode(_component_app_icon, {
                                name: "map-pin-ink",
                                size: "26rpx"
                              }),
                              vue.createElementVNode(
                                "text",
                                { class: "m-place" },
                                vue.toDisplayString(m.place),
                                1
                                /* TEXT */
                              )
                            ]),
                            vue.createElementVNode("view", { class: "flex items-center m-row" }, [
                              vue.createVNode(_component_app_icon, {
                                name: m.highlight ? "sparkles-gold" : "file-ink",
                                size: "24rpx"
                              }, null, 8, ["name"]),
                              vue.createElementVNode(
                                "text",
                                { class: "m-note" },
                                vue.toDisplayString(m.note),
                                1
                                /* TEXT */
                              )
                            ]),
                            vue.createElementVNode("view", {
                              class: "flex",
                              style: { "gap": "28rpx", "margin-top": "20rpx" }
                            }, [
                              vue.createElementVNode("view", {
                                class: "m-link flex items-center",
                                "hover-class": "hover-opacity",
                                onClick: ($event) => $setup.onDetail(m)
                              }, [
                                vue.createElementVNode("text", { class: "m-link-primary" }, "查看详情"),
                                vue.createVNode(_component_app_icon, {
                                  name: "chevron-right",
                                  size: "24rpx"
                                })
                              ], 8, ["onClick"]),
                              vue.createElementVNode("view", {
                                class: "m-link flex items-center",
                                "hover-class": "hover-opacity",
                                onClick: ($event) => $setup.onContact(m)
                              }, [
                                vue.createVNode(_component_app_icon, {
                                  name: "phone-gold",
                                  size: "24rpx"
                                }),
                                vue.createElementVNode("text", { class: "m-link-gold" }, "联系会员")
                              ], 8, ["onClick"])
                            ])
                          ]),
                          _: 2
                          /* DYNAMIC */
                        },
                        1024
                        /* DYNAMIC_SLOTS */
                      )
                    ]);
                  }),
                  128
                  /* KEYED_FRAGMENT */
                ))
              ],
              64
              /* STABLE_FRAGMENT */
            );
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        $setup.grouped.length === 0 ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty flex-center"
        }, [
          vue.createElementVNode("text", { class: "empty-text" }, "当前筛选下暂无约见安排")
        ])) : vue.createCommentVNode("v-if", true)
      ]),
      vue.createElementVNode("view", {
        class: "fab flex-center",
        "hover-class": "hover-scale",
        onClick: $setup.onNew
      }, [
        vue.createVNode(_component_app_icon, {
          name: "plus-white",
          size: "48rpx"
        })
      ]),
      vue.createElementVNode("view", { style: { "height": "160rpx" } })
    ]);
  }
  const PagesScheduleIndex = /* @__PURE__ */ _export_sfc(_sfc_main$7, [["render", _sfc_render$6], ["__scopeId", "data-v-d5b2057b"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/schedule/index.vue"]]);
  const _sfc_main$6 = /* @__PURE__ */ vue.defineComponent({
    __name: "kanban",
    setup(__props, { expose: __expose }) {
      __expose();
      const statusBarHeight = vue.ref(0);
      try {
        statusBarHeight.value = uni.getWindowInfo().statusBarHeight ?? 0;
      } catch {
        statusBarHeight.value = 0;
      }
      const tabs = ["牵线中", "待反馈", "已约见", "已脱单"];
      const activeTab = vue.ref("牵线中");
      const STATUS_NORMALIZE = {
        牵线中: "牵线中",
        待反馈: "待反馈",
        已约见: "已约见",
        已脱单: "已脱单",
        已终止: "已终止",
        matching: "牵线中",
        pending: "待反馈",
        met: "已约见",
        success: "已脱单",
        failed: "已终止",
        done: "已脱单"
      };
      const normalizeStatus = (s2) => STATUS_NORMALIZE[s2 || ""] || "牵线中";
      const cards = vue.ref([]);
      const loading = vue.ref(false);
      const fetchPairs = async () => {
        loading.value = true;
        try {
          const res = await api.pairs();
          cards.value = (res.list || []).map((p) => {
            const status = normalizeStatus(p.status);
            return {
              id: String(p.id),
              male: { id: p.maleId || "", name: p.maleName || "男嘉宾", avatarBg: "", avatarColor: "" },
              female: { id: p.femaleId || "", name: p.femaleName || "女嘉宾", avatarBg: "", avatarColor: "" },
              matchScore: typeof p.score === "number" ? p.score : 0,
              status,
              statusLabel: p.statusLabel || status
            };
          });
        } catch {
          uni.showToast({ title: "后端未连接", icon: "none" });
        } finally {
          loading.value = false;
        }
      };
      onShow(fetchPairs);
      const filteredCards = vue.computed(() => cards.value.filter((c) => c.status === activeTab.value));
      const LABEL_MAP = { call: "跟进", schedule: "约见", done: "完成", fail: "失败" };
      const ACTION_STATUS = { call: "牵线中", schedule: "已约见", done: "已脱单", fail: "已终止" };
      const onAction = async (type, c) => {
        const next = ACTION_STATUS[type];
        const label = LABEL_MAP[type];
        try {
          await api.updatePairStatus(String(c.id), next);
          c.status = next;
          c.statusLabel = next;
          uni.showToast({ title: `${label}成功`, icon: "success" });
        } catch {
          uni.showToast({ title: `${label}失败`, icon: "none" });
        }
      };
      const __returned__ = { statusBarHeight, tabs, activeTab, STATUS_NORMALIZE, normalizeStatus, cards, loading, fetchPairs, filteredCards, LABEL_MAP, ACTION_STATUS, onAction };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$5(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    const _component_loading_red_line = resolveEasycom(vue.resolveDynamicComponent("loading-red-line"), __easycom_1);
    const _component_progress_ring = resolveEasycom(vue.resolveDynamicComponent("progress-ring"), __easycom_2);
    const _component_glass_card = resolveEasycom(vue.resolveDynamicComponent("glass-card"), __easycom_3);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg),
      $setup.loading ? (vue.openBlock(), vue.createBlock(_component_loading_red_line, {
        key: 0,
        top: $setup.statusBarHeight + "px"
      }, null, 8, ["top"])) : vue.createCommentVNode("v-if", true),
      vue.createElementVNode(
        "view",
        {
          style: vue.normalizeStyle({ height: $setup.statusBarHeight + "px" })
        },
        null,
        4
        /* STYLE */
      ),
      vue.createElementVNode("view", { class: "seg-wrap" }, [
        vue.createElementVNode("view", { class: "seg" }, [
          (vue.openBlock(), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.tabs, (t) => {
              return vue.createElementVNode("view", {
                key: t,
                class: vue.normalizeClass(["seg-item flex-center", { "seg-active": $setup.activeTab === t }]),
                onClick: ($event) => $setup.activeTab = t
              }, vue.toDisplayString(t), 11, ["onClick"]);
            }),
            64
            /* STABLE_FRAGMENT */
          ))
        ])
      ]),
      vue.createElementVNode("view", { class: "card-list" }, [
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.filteredCards, (c) => {
            return vue.openBlock(), vue.createBlock(
              _component_glass_card,
              {
                key: c.id,
                strong: "",
                padding: "32rpx",
                shadow: "normal",
                class: "pair-card"
              },
              {
                default: vue.withCtx(() => [
                  vue.createElementVNode("view", { class: "flex items-center" }, [
                    vue.createElementVNode("view", { class: "duo-avatar" }, [
                      vue.createElementVNode(
                        "view",
                        {
                          class: "duo duo-male flex-center",
                          style: vue.normalizeStyle({ background: c.male.avatarBg })
                        },
                        vue.toDisplayString(c.male.name[0]),
                        5
                        /* TEXT, STYLE */
                      ),
                      vue.createElementVNode(
                        "view",
                        {
                          class: "duo duo-female flex-center",
                          style: vue.normalizeStyle({ background: c.female.avatarBg })
                        },
                        vue.toDisplayString(c.female.name[0]),
                        5
                        /* TEXT, STYLE */
                      )
                    ]),
                    vue.createElementVNode("view", { class: "pair-info" }, [
                      vue.createElementVNode(
                        "text",
                        { class: "pair-names" },
                        vue.toDisplayString(c.male.name) + " × " + vue.toDisplayString(c.female.name),
                        1
                        /* TEXT */
                      ),
                      vue.createElementVNode("view", {
                        class: "flex items-center",
                        style: { "gap": "16rpx", "margin-top": "12rpx" }
                      }, [
                        vue.createVNode(_component_progress_ring, {
                          percent: c.matchScore,
                          size: "84rpx",
                          stroke: "8rpx"
                        }, null, 8, ["percent"]),
                        vue.createElementVNode("view", null, [
                          vue.createElementVNode("text", { class: "match-caption" }, "匹配度"),
                          vue.createElementVNode(
                            "text",
                            { class: "match-hint" },
                            vue.toDisplayString(c.matchScore >= 90 ? "天作之合" : c.matchScore >= 80 ? "十分契合" : "值得推进"),
                            1
                            /* TEXT */
                          )
                        ])
                      ])
                    ]),
                    vue.createElementVNode(
                      "view",
                      { class: "status-tag" },
                      vue.toDisplayString(c.statusLabel),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode("view", { class: "op-row" }, [
                    vue.createElementVNode("view", {
                      class: "op-btn flex-center",
                      "hover-class": "hover-opacity",
                      onClick: ($event) => $setup.onAction("call", c)
                    }, "📞 跟进", 8, ["onClick"]),
                    vue.createElementVNode("view", {
                      class: "op-btn flex-center",
                      "hover-class": "hover-opacity",
                      onClick: ($event) => $setup.onAction("schedule", c)
                    }, "📅 约见", 8, ["onClick"]),
                    vue.createElementVNode("view", {
                      class: "op-btn op-success flex-center",
                      "hover-class": "hover-opacity",
                      onClick: ($event) => $setup.onAction("done", c)
                    }, "✅ 完成", 8, ["onClick"]),
                    vue.createElementVNode("view", {
                      class: "op-btn op-danger flex-center",
                      "hover-class": "hover-opacity",
                      onClick: ($event) => $setup.onAction("fail", c)
                    }, "❌ 失败", 8, ["onClick"])
                  ])
                ]),
                _: 2
                /* DYNAMIC */
              },
              1024
              /* DYNAMIC_SLOTS */
            );
          }),
          128
          /* KEYED_FRAGMENT */
        )),
        $setup.filteredCards.length === 0 ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty flex-center"
        }, [
          vue.createElementVNode("text", { class: "empty-text" }, "当前状态下暂无牵线记录")
        ])) : vue.createCommentVNode("v-if", true)
      ]),
      vue.createElementVNode("view", { style: { "height": "40rpx" } })
    ]);
  }
  const PagesFollowupKanban = /* @__PURE__ */ _export_sfc(_sfc_main$6, [["render", _sfc_render$5], ["__scopeId", "data-v-03e93911"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/followup/kanban.vue"]]);
  const memberApi = {
    /** 会员登录：手机号 或 微信 openid */
    login: (data) => request({
      url: "/api/auth/member-login",
      method: "POST",
      data
    }),
    /** 本人资料 */
    me: () => request({ url: "/api/member/me" }),
    /** 推给我的牵线（含对方画像 + 我的表态） */
    pairs: () => request({ url: "/api/member/pairs" }),
    /** 对牵线表态：接受 / 暂不 */
    respond: (id, kind, note) => request({
      url: `/api/member/pairs/${id}/respond`,
      method: "POST",
      data: { kind, note }
    }),
    /** 可报名活动（含 enrolled 标记） */
    activities: () => request({ url: "/api/member/activities" }),
    /** 报名活动 */
    enroll: (id) => request({
      url: `/api/member/activities/${id}/enroll`,
      method: "POST"
    }),
    /** 我的认证状态总览 */
    verify: () => request({ url: "/api/member/verify" }),
    /** 自动实人认证（2-A）：上传活体照 + 活体令牌，点亮「已实人」徽章 */
    realPerson: (data) => request({ url: "/api/verify/real-person", method: "POST", data }),
    /** 我的专属推荐码（2-B 全民红娘裂变） */
    myCode: () => request({ url: "/api/referral/code" }),
    /** C 端裂变：好友凭码登记为被推荐人 */
    registerByCode: (data) => request({ url: "/api/referral/register-by-code", method: "POST", data }),
    /** 我的推荐记录 */
    myReferrals: () => request({ url: "/api/referral/mine" }),
    /** 我的积分（含推荐成功奖励明细） */
    myPoints: () => request({ url: "/api/member/points" })
  };
  function memberLoginAndStore(r) {
    setToken(r.token);
    setStoredUser(r.user);
  }
  const _sfc_main$5 = /* @__PURE__ */ vue.defineComponent({
    __name: "login",
    setup(__props, { expose: __expose }) {
      __expose();
      const phone = vue.ref("");
      const loading = vue.ref(false);
      const error = vue.ref("");
      async function doLogin() {
        error.value = "";
        const p = phone.value.trim();
        if (!/^1\d{10}$/.test(p)) {
          error.value = "请输入正确的 11 位手机号";
          return;
        }
        loading.value = true;
        try {
          const r = await memberApi.login({ phone: p });
          memberLoginAndStore(r);
          uni.showToast({ title: `您好，${r.user.name}`, icon: "none" });
          setTimeout(() => uni.reLaunch({ url: "/pages/member-portal/home" }), 400);
        } catch (e) {
          error.value = errMsg(e);
        } finally {
          loading.value = false;
        }
      }
      const __returned__ = { phone, loading, error, doLogin };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$4(_ctx, _cache, $props, $setup, $data, $options) {
    const _component_aurora_bg = resolveEasycom(vue.resolveDynamicComponent("aurora-bg"), __easycom_0);
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createVNode(_component_aurora_bg, { dense: "" }),
      vue.createElementVNode("view", { class: "brand" }, [
        vue.createElementVNode("view", { class: "logo-wrap anim-float" }, [
          vue.createElementVNode("view", { class: "logo-glow" }),
          vue.createElementVNode("view", { class: "logo" }, "鹊")
        ]),
        vue.createElementVNode("text", { class: "brand-title" }, "鹊桥 · 会员端"),
        vue.createElementVNode("text", { class: "brand-sub" }, "专属你的缘分，触手可及")
      ]),
      vue.createElementVNode("view", { class: "form-shell" }, [
        vue.createElementVNode("view", { class: "form-shine" }),
        vue.createElementVNode("view", { class: "form-inner" }, [
          vue.createElementVNode("view", { class: "field" }, [
            vue.createElementVNode("text", { class: "label" }, "手机号"),
            vue.withDirectives(vue.createElementVNode(
              "input",
              {
                class: "input",
                "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.phone = $event),
                type: "number",
                placeholder: "请输入建档时登记的手机号",
                "placeholder-class": "ph",
                maxlength: 11,
                "confirm-type": "done",
                onConfirm: $setup.doLogin
              },
              null,
              544
              /* NEED_HYDRATION, NEED_PATCH */
            ), [
              [vue.vModelText, $setup.phone]
            ])
          ]),
          $setup.error ? (vue.openBlock(), vue.createElementBlock(
            "view",
            {
              key: 0,
              class: "error"
            },
            vue.toDisplayString($setup.error),
            1
            /* TEXT */
          )) : vue.createCommentVNode("v-if", true),
          vue.createElementVNode("button", {
            class: "btn",
            disabled: $setup.loading,
            onClick: $setup.doLogin
          }, vue.toDisplayString($setup.loading ? "登录中…" : "登 录"), 9, ["disabled"]),
          vue.createElementVNode("text", { class: "tip" }, "登录即代表你已阅读并同意《用户协议》与《隐私政策》")
        ])
      ])
    ]);
  }
  const PagesMemberPortalLogin = /* @__PURE__ */ _export_sfc(_sfc_main$5, [["render", _sfc_render$4], ["__scopeId", "data-v-267c5432"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/member-portal/login.vue"]]);
  const _sfc_main$4 = /* @__PURE__ */ vue.defineComponent({
    __name: "home",
    setup(__props, { expose: __expose }) {
      __expose();
      const tab = vue.ref("pairs");
      const loading = vue.ref(false);
      const pairs = vue.ref([]);
      const activities = vue.ref([]);
      const me = vue.ref(null);
      const verify = vue.ref(null);
      async function loadAll2() {
        loading.value = true;
        try {
          const [p, a, m, v] = await Promise.all([
            memberApi.pairs().catch(() => []),
            memberApi.activities().catch(() => []),
            memberApi.me().catch(() => null),
            memberApi.verify().catch(() => null)
          ]);
          pairs.value = p;
          activities.value = a;
          me.value = m;
          verify.value = v;
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          loading.value = false;
        }
      }
      onShow(() => loadAll2());
      function openPair(p) {
        var _a;
        const id = (_a = p.pair) == null ? void 0 : _a.id;
        if (id)
          uni.navigateTo({ url: `/pages/member-portal/match-detail?id=${id}` });
      }
      async function enroll(a) {
        const act = a.activity;
        try {
          const r = await memberApi.enroll(act.id);
          uni.showToast({ title: r.already ? "你已报名" : "报名成功", icon: "none" });
          a.enrolled = true;
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        }
      }
      function initial(name) {
        return name ? name.slice(0, 1) : "?";
      }
      function goReferral() {
        uni.navigateTo({ url: "/pages/referral/index" });
      }
      function genderTag(g) {
        return g === "男" ? "♂" : g === "女" ? "♀" : "";
      }
      const verifyItems = [
        { key: "verifyRealName", label: "实名认证" },
        { key: "verifyEducation", label: "学历认证" },
        { key: "verifyAsset", label: "资产认证" },
        { key: "verifyRealPerson", label: "实人认证" }
      ];
      async function doRealPerson() {
        uni.chooseImage({
          count: 1,
          sourceType: ["camera", "album"],
          success: async (res) => {
            var _a;
            const path = res.tempFilePaths && res.tempFilePaths[0] || res.tempFiles && ((_a = res.tempFiles[0]) == null ? void 0 : _a.path);
            if (!path)
              return;
            let base64 = "";
            try {
              base64 = await new Promise((resolve, reject) => {
                uni.getFileSystemManager().readFile({
                  filePath: path,
                  encoding: "base64",
                  success: (r) => resolve("data:image/jpeg;base64," + r.data),
                  fail: () => reject(new Error("读取图片失败"))
                });
              });
            } catch {
              uni.showToast({ title: "读取活体照失败", icon: "none" });
              return;
            }
            try {
              uni.showLoading({ title: "认证中…", mask: true });
              const r = await memberApi.realPerson({ authToken: "", liveImage: base64 });
              await loadAll2();
              uni.hideLoading();
              const st = r == null ? void 0 : r.status;
              if (st === "已通过")
                uni.showToast({ title: "实人认证通过", icon: "success" });
              else if (st === "待核验")
                uni.showToast({ title: "已提交，等待人工审核", icon: "none" });
              else
                uni.showToast({ title: "实人认证未通过", icon: "none" });
            } catch (e) {
              uni.hideLoading();
              uni.showToast({ title: errMsg(e), icon: "none" });
            }
          },
          fail: () => {
          }
        });
      }
      const __returned__ = { tab, loading, pairs, activities, me, verify, loadAll: loadAll2, openPair, enroll, initial, goReferral, genderTag, verifyItems, doRealPerson, get maskPhone() {
        return maskPhone;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$3(_ctx, _cache, $props, $setup, $data, $options) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k;
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "topbar" }, [
        vue.createElementVNode(
          "text",
          { class: "hi" },
          "你好，" + vue.toDisplayString(((_a = $setup.me) == null ? void 0 : _a.name) || "会员"),
          1
          /* TEXT */
        ),
        vue.createElementVNode("text", { class: "sub" }, "为你推送的缘分都在这里")
      ]),
      vue.createElementVNode("view", { class: "tabs" }, [
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab", { active: $setup.tab === "pairs" }]),
            onClick: _cache[0] || (_cache[0] = ($event) => $setup.tab = "pairs")
          },
          "牵线",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab", { active: $setup.tab === "activities" }]),
            onClick: _cache[1] || (_cache[1] = ($event) => $setup.tab = "activities")
          },
          "活动",
          2
          /* CLASS */
        ),
        vue.createElementVNode(
          "view",
          {
            class: vue.normalizeClass(["tab", { active: $setup.tab === "mine" }]),
            onClick: _cache[2] || (_cache[2] = ($event) => $setup.tab = "mine")
          },
          "我的",
          2
          /* CLASS */
        )
      ]),
      $setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "empty"
      }, "加载中…")) : $setup.tab === "pairs" ? (vue.openBlock(), vue.createElementBlock("view", { key: 1 }, [
        !$setup.pairs.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, "暂无进行中的牵线，红娘正在为你物色～")) : vue.createCommentVNode("v-if", true),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.pairs, (p) => {
            var _a2, _b2, _c2, _d2, _e2, _f2, _g2, _h2, _i2, _j2, _k2;
            return vue.openBlock(), vue.createElementBlock("view", {
              key: (_a2 = p.pair) == null ? void 0 : _a2.id,
              class: "card",
              onClick: ($event) => $setup.openPair(p)
            }, [
              vue.createElementVNode(
                "view",
                {
                  class: "avatar",
                  style: vue.normalizeStyle({ background: ((_b2 = p.other) == null ? void 0 : _b2.avatarBg) || "#8b254a" })
                },
                vue.toDisplayString($setup.initial((_c2 = p.other) == null ? void 0 : _c2.name)),
                5
                /* TEXT, STYLE */
              ),
              vue.createElementVNode("view", { class: "info" }, [
                vue.createElementVNode("view", { class: "name" }, [
                  vue.createTextVNode(
                    vue.toDisplayString((_d2 = p.other) == null ? void 0 : _d2.name) + " ",
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "gt" },
                    vue.toDisplayString($setup.genderTag((_e2 = p.other) == null ? void 0 : _e2.gender)),
                    1
                    /* TEXT */
                  ),
                  vue.createElementVNode(
                    "text",
                    { class: "age" },
                    vue.toDisplayString((_f2 = p.other) == null ? void 0 : _f2.age) + "岁",
                    1
                    /* TEXT */
                  )
                ]),
                vue.createElementVNode(
                  "view",
                  { class: "meta" },
                  vue.toDisplayString((_g2 = p.other) == null ? void 0 : _g2.city) + " · " + vue.toDisplayString((_h2 = p.other) == null ? void 0 : _h2.education) + " · " + vue.toDisplayString((_i2 = p.other) == null ? void 0 : _i2.job),
                  1
                  /* TEXT */
                ),
                ((_j2 = p.other) == null ? void 0 : _j2.tags) ? (vue.openBlock(), vue.createElementBlock(
                  "view",
                  {
                    key: 0,
                    class: "tags"
                  },
                  vue.toDisplayString(p.other.tags),
                  1
                  /* TEXT */
                )) : vue.createCommentVNode("v-if", true),
                vue.createElementVNode("view", { class: "status" }, [
                  vue.createTextVNode(
                    " 状态：" + vue.toDisplayString((_k2 = p.pair) == null ? void 0 : _k2.status) + " ",
                    1
                    /* TEXT */
                  ),
                  p.myResponseKind ? (vue.openBlock(), vue.createElementBlock(
                    "text",
                    {
                      key: 0,
                      class: "mine"
                    },
                    "（我已" + vue.toDisplayString(p.myResponseKind === "success" ? "接受" : "暂不") + "）",
                    1
                    /* TEXT */
                  )) : vue.createCommentVNode("v-if", true)
                ])
              ]),
              vue.createElementVNode("text", { class: "arrow" }, "›")
            ], 8, ["onClick"]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])) : $setup.tab === "activities" ? (vue.openBlock(), vue.createElementBlock("view", { key: 2 }, [
        !$setup.activities.length ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "empty"
        }, "近期暂无开放活动")) : vue.createCommentVNode("v-if", true),
        (vue.openBlock(true), vue.createElementBlock(
          vue.Fragment,
          null,
          vue.renderList($setup.activities, (a) => {
            var _a2, _b2, _c2, _d2, _e2, _f2, _g2;
            return vue.openBlock(), vue.createElementBlock("view", {
              key: (_a2 = a.activity) == null ? void 0 : _a2.id,
              class: "card col"
            }, [
              vue.createElementVNode(
                "view",
                { class: "act-title" },
                vue.toDisplayString((_b2 = a.activity) == null ? void 0 : _b2.title),
                1
                /* TEXT */
              ),
              vue.createElementVNode(
                "view",
                { class: "meta" },
                vue.toDisplayString((_c2 = a.activity) == null ? void 0 : _c2.date) + " · " + vue.toDisplayString((_d2 = a.activity) == null ? void 0 : _d2.time) + " · " + vue.toDisplayString((_e2 = a.activity) == null ? void 0 : _e2.place),
                1
                /* TEXT */
              ),
              vue.createElementVNode("view", { class: "act-foot" }, [
                vue.createElementVNode(
                  "text",
                  { class: "cap" },
                  "已报名 " + vue.toDisplayString(((_f2 = a.activity) == null ? void 0 : _f2.signed) || 0) + "/" + vue.toDisplayString(((_g2 = a.activity) == null ? void 0 : _g2.capacity) || "—"),
                  1
                  /* TEXT */
                ),
                !a.enrolled ? (vue.openBlock(), vue.createElementBlock("button", {
                  key: 0,
                  class: "mini",
                  onClick: vue.withModifiers(($event) => $setup.enroll(a), ["stop"])
                }, "报名", 8, ["onClick"])) : (vue.openBlock(), vue.createElementBlock("text", {
                  key: 1,
                  class: "done"
                }, "已报名"))
              ])
            ]);
          }),
          128
          /* KEYED_FRAGMENT */
        ))
      ])) : (vue.openBlock(), vue.createElementBlock("view", {
        key: 3,
        class: "mine-wrap"
      }, [
        vue.createElementVNode("view", { class: "card col" }, [
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "k" }, "姓名"),
            vue.createElementVNode(
              "text",
              { class: "v" },
              vue.toDisplayString((_b = $setup.me) == null ? void 0 : _b.name),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "k" }, "性别"),
            vue.createElementVNode(
              "text",
              { class: "v" },
              vue.toDisplayString((_c = $setup.me) == null ? void 0 : _c.gender) + " · " + vue.toDisplayString((_d = $setup.me) == null ? void 0 : _d.age) + "岁",
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "k" }, "城市"),
            vue.createElementVNode(
              "text",
              { class: "v" },
              vue.toDisplayString((_e = $setup.me) == null ? void 0 : _e.city),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "k" }, "手机"),
            vue.createElementVNode(
              "text",
              { class: "v" },
              vue.toDisplayString($setup.maskPhone((_f = $setup.me) == null ? void 0 : _f.phone)),
              1
              /* TEXT */
            )
          ]),
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "k" }, "等级"),
            vue.createElementVNode(
              "text",
              { class: "v" },
              vue.toDisplayString(((_g = $setup.me) == null ? void 0 : _g.level) || "—"),
              1
              /* TEXT */
            )
          ])
        ]),
        vue.createElementVNode("view", { class: "card col" }, [
          vue.createElementVNode("view", { class: "sec-title" }, "认证状态"),
          (vue.openBlock(), vue.createElementBlock(
            vue.Fragment,
            null,
            vue.renderList($setup.verifyItems, (it) => {
              var _a2, _b2;
              return vue.createElementVNode("view", {
                key: it.key,
                class: "row"
              }, [
                vue.createElementVNode(
                  "text",
                  { class: "k" },
                  vue.toDisplayString(it.label),
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  {
                    class: vue.normalizeClass(["v", ((_a2 = $setup.verify) == null ? void 0 : _a2[it.key]) ? "ok" : "no"])
                  },
                  vue.toDisplayString(((_b2 = $setup.verify) == null ? void 0 : _b2[it.key]) ? "已认证" : "未认证"),
                  3
                  /* TEXT, CLASS */
                )
              ]);
            }),
            64
            /* STABLE_FRAGMENT */
          )),
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "k" }, "综合认证"),
            vue.createElementVNode(
              "text",
              {
                class: vue.normalizeClass(["v", ((_h = $setup.verify) == null ? void 0 : _h.verified) ? "ok" : "no"])
              },
              vue.toDisplayString(((_i = $setup.verify) == null ? void 0 : _i.verified) ? "已认证" : "未认证"),
              3
              /* TEXT, CLASS */
            )
          ]),
          vue.createElementVNode(
            "button",
            {
              class: "real-btn",
              onClick: $setup.doRealPerson
            },
            vue.toDisplayString(((_j = $setup.verify) == null ? void 0 : _j.verifyRealPerson) ? "重新实人认证" : "去实人认证"),
            1
            /* TEXT */
          )
        ]),
        vue.createElementVNode("view", { class: "card col" }, [
          vue.createElementVNode("view", { class: "row" }, [
            vue.createElementVNode("text", { class: "sec-title" }, "邀请有礼"),
            vue.createElementVNode("text", { class: "arrow-sm" }, "›")
          ]),
          vue.createElementVNode("text", { class: "hint" }, "邀请好友注册并开通会员，你得积分奖励"),
          vue.createElementVNode("button", {
            class: "ref-btn",
            onClick: $setup.goReferral
          }, "我的专属邀请码")
        ]),
        ((_k = $setup.me) == null ? void 0 : _k.bio) ? (vue.openBlock(), vue.createElementBlock("view", {
          key: 0,
          class: "card col"
        }, [
          vue.createElementVNode("view", { class: "sec-title" }, "自我介绍"),
          vue.createElementVNode(
            "text",
            { class: "bio" },
            vue.toDisplayString($setup.me.bio),
            1
            /* TEXT */
          )
        ])) : vue.createCommentVNode("v-if", true)
      ]))
    ]);
  }
  const PagesMemberPortalHome = /* @__PURE__ */ _export_sfc(_sfc_main$4, [["render", _sfc_render$3], ["__scopeId", "data-v-4089b61d"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/member-portal/home.vue"]]);
  const _sfc_main$3 = /* @__PURE__ */ vue.defineComponent({
    __name: "match-detail",
    setup(__props, { expose: __expose }) {
      __expose();
      const id = vue.ref("");
      const item = vue.ref(null);
      const loading = vue.ref(false);
      const acting = vue.ref(false);
      onLoad((opt) => {
        id.value = (opt == null ? void 0 : opt.id) || "";
        load();
      });
      async function load() {
        loading.value = true;
        try {
          const list = await memberApi.pairs().catch(() => []);
          item.value = list.find((p) => {
            var _a;
            return ((_a = p.pair) == null ? void 0 : _a.id) === id.value;
          }) || null;
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          loading.value = false;
        }
      }
      async function respond(kind) {
        if (acting.value)
          return;
        acting.value = true;
        try {
          const r = await memberApi.respond(id.value, kind);
          uni.showToast({ title: kind === "success" ? "已接受" : "已暂不", icon: "none" });
          item.value = { ...item.value, pair: r.pair, myResponseKind: kind };
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          acting.value = false;
        }
      }
      function initial(name) {
        return name ? name.slice(0, 1) : "?";
      }
      function genderTag(g) {
        return g === "男" ? "♂" : g === "女" ? "♀" : "";
      }
      const __returned__ = { id, item, loading, acting, load, respond, initial, genderTag };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$2(_ctx, _cache, $props, $setup, $data, $options) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _i, _j, _k;
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "nav" }, [
        vue.createElementVNode("text", {
          class: "back",
          onClick: _cache[0] || (_cache[0] = ($event) => uni.navigateBack())
        }, "‹ 返回"),
        vue.createElementVNode("text", { class: "title" }, "牵线详情")
      ]),
      $setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "empty"
      }, "加载中…")) : !$setup.item ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 1,
        class: "empty"
      }, "未找到该牵线")) : (vue.openBlock(), vue.createElementBlock(
        vue.Fragment,
        { key: 2 },
        [
          vue.createElementVNode("view", { class: "hero card" }, [
            vue.createElementVNode(
              "view",
              {
                class: "avatar",
                style: vue.normalizeStyle({ background: ((_a = $setup.item.other) == null ? void 0 : _a.avatarBg) || "#8b254a" })
              },
              vue.toDisplayString($setup.initial((_b = $setup.item.other) == null ? void 0 : _b.name)),
              5
              /* TEXT, STYLE */
            ),
            vue.createElementVNode("view", { class: "hinfo" }, [
              vue.createElementVNode("view", { class: "name" }, [
                vue.createTextVNode(
                  vue.toDisplayString((_c = $setup.item.other) == null ? void 0 : _c.name) + " ",
                  1
                  /* TEXT */
                ),
                vue.createElementVNode(
                  "text",
                  { class: "gt" },
                  vue.toDisplayString($setup.genderTag((_d = $setup.item.other) == null ? void 0 : _d.gender)),
                  1
                  /* TEXT */
                ),
                vue.createTextVNode(),
                vue.createElementVNode(
                  "text",
                  { class: "age" },
                  vue.toDisplayString((_e = $setup.item.other) == null ? void 0 : _e.age) + "岁",
                  1
                  /* TEXT */
                )
              ]),
              vue.createElementVNode(
                "view",
                { class: "meta" },
                vue.toDisplayString((_f = $setup.item.other) == null ? void 0 : _f.city) + " · " + vue.toDisplayString((_g = $setup.item.other) == null ? void 0 : _g.education) + " · " + vue.toDisplayString((_h = $setup.item.other) == null ? void 0 : _h.job),
                1
                /* TEXT */
              ),
              ((_i = $setup.item.other) == null ? void 0 : _i.tags) ? (vue.openBlock(), vue.createElementBlock(
                "view",
                {
                  key: 0,
                  class: "tags"
                },
                vue.toDisplayString($setup.item.other.tags),
                1
                /* TEXT */
              )) : vue.createCommentVNode("v-if", true)
            ])
          ]),
          vue.createElementVNode("view", { class: "card col" }, [
            vue.createElementVNode("view", { class: "sec-title" }, "匹配进度"),
            vue.createElementVNode(
              "view",
              { class: "meta" },
              "当前状态：" + vue.toDisplayString((_j = $setup.item.pair) == null ? void 0 : _j.status),
              1
              /* TEXT */
            ),
            $setup.item.myResponseKind ? (vue.openBlock(), vue.createElementBlock(
              "view",
              {
                key: 0,
                class: "mine"
              },
              "你的表态：" + vue.toDisplayString($setup.item.myResponseKind === "success" ? "已接受" : "暂不"),
              1
              /* TEXT */
            )) : vue.createCommentVNode("v-if", true)
          ]),
          ((_k = $setup.item.other) == null ? void 0 : _k.bio) ? (vue.openBlock(), vue.createElementBlock("view", {
            key: 0,
            class: "card col"
          }, [
            vue.createElementVNode("view", { class: "sec-title" }, "TA 的自我介绍"),
            vue.createElementVNode(
              "text",
              { class: "bio" },
              vue.toDisplayString($setup.item.other.bio),
              1
              /* TEXT */
            )
          ])) : vue.createCommentVNode("v-if", true),
          vue.createElementVNode("view", { class: "actions" }, [
            vue.createElementVNode("button", {
              class: "act accept",
              disabled: $setup.acting,
              onClick: _cache[1] || (_cache[1] = ($event) => $setup.respond("success"))
            }, "接受牵线", 8, ["disabled"]),
            vue.createElementVNode("button", {
              class: "act later",
              disabled: $setup.acting,
              onClick: _cache[2] || (_cache[2] = ($event) => $setup.respond("reject"))
            }, "暂不考虑", 8, ["disabled"])
          ])
        ],
        64
        /* STABLE_FRAGMENT */
      ))
    ]);
  }
  const PagesMemberPortalMatchDetail = /* @__PURE__ */ _export_sfc(_sfc_main$3, [["render", _sfc_render$2], ["__scopeId", "data-v-826e5c97"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/member-portal/match-detail.vue"]]);
  const _sfc_main$2 = /* @__PURE__ */ vue.defineComponent({
    __name: "index",
    setup(__props, { expose: __expose }) {
      __expose();
      const loading = vue.ref(false);
      const code = vue.ref("");
      const referrals = vue.ref([]);
      const points = vue.ref(null);
      const stats = vue.computed(() => {
        const list = referrals.value || [];
        const converted = list.filter((r) => r.status === "已转化" || r.status === "已发奖").length;
        const rewarded = list.filter((r) => r.status === "已发奖").length;
        return { total: list.length, converted, rewarded };
      });
      const referralLogs = vue.computed(() => {
        var _a;
        const logs = ((_a = points.value) == null ? void 0 : _a.logs) || [];
        return logs.filter((l) => l.type === "REFERRAL_SUCCESS");
      });
      async function loadAll2() {
        loading.value = true;
        try {
          const [c, r, p] = await Promise.all([
            memberApi.myCode().catch(() => ({ code: "" })),
            memberApi.myReferrals().catch(() => []),
            memberApi.myPoints().catch(() => null)
          ]);
          code.value = (c == null ? void 0 : c.code) || "";
          referrals.value = r || [];
          points.value = p;
        } catch (e) {
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          loading.value = false;
        }
      }
      onShow(() => loadAll2());
      function copyCode() {
        if (!code.value)
          return;
        uni.setClipboardData({
          data: code.value,
          success: () => uni.showToast({ title: "推荐码已复制", icon: "none" })
        });
      }
      function share() {
        if (!code.value)
          return;
        const link = `/pages/referral/register?code=${code.value}`;
        uni.showActionSheet({
          itemList: ["复制推荐码", "复制邀请链接"],
          success: (res) => {
            if (res.tapIndex === 0)
              copyCode();
            else
              uni.setClipboardData({
                data: link,
                success: () => uni.showToast({ title: "邀请链接已复制", icon: "none" })
              });
          }
        });
      }
      function statusClass(s2) {
        if (s2 === "已发奖")
          return "ok";
        if (s2 === "已转化")
          return "conv";
        return "reg";
      }
      const __returned__ = { loading, code, referrals, points, stats, referralLogs, loadAll: loadAll2, copyCode, share, statusClass, get maskPhone() {
        return maskPhone;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render$1(_ctx, _cache, $props, $setup, $data, $options) {
    var _a, _b;
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "hero" }, [
        vue.createElementVNode("text", { class: "title" }, "邀请好友 · 得积分"),
        vue.createElementVNode("text", { class: "sub" }, "好友注册并开通会员，你即可获得推荐积分奖励")
      ]),
      $setup.loading ? (vue.openBlock(), vue.createElementBlock("view", {
        key: 0,
        class: "empty"
      }, "加载中…")) : (vue.openBlock(), vue.createElementBlock(
        vue.Fragment,
        { key: 1 },
        [
          vue.createElementVNode("view", { class: "card code-card" }, [
            vue.createElementVNode("view", { class: "sec-title" }, "我的专属邀请码"),
            vue.createElementVNode("view", { class: "code-box" }, [
              vue.createElementVNode(
                "text",
                { class: "code" },
                vue.toDisplayString($setup.code || "———"),
                1
                /* TEXT */
              )
            ]),
            vue.createElementVNode("view", { class: "code-actions" }, [
              vue.createElementVNode("button", {
                class: "btn ghost",
                onClick: $setup.copyCode
              }, "复制推荐码"),
              vue.createElementVNode("button", {
                class: "btn primary",
                onClick: $setup.share
              }, "分享邀请")
            ])
          ]),
          vue.createElementVNode("view", { class: "stats" }, [
            vue.createElementVNode("view", { class: "stat" }, [
              vue.createElementVNode(
                "text",
                { class: "num" },
                vue.toDisplayString($setup.stats.total),
                1
                /* TEXT */
              ),
              vue.createElementVNode("text", { class: "lbl" }, "累计邀请")
            ]),
            vue.createElementVNode("view", { class: "stat" }, [
              vue.createElementVNode(
                "text",
                { class: "num" },
                vue.toDisplayString($setup.stats.converted),
                1
                /* TEXT */
              ),
              vue.createElementVNode("text", { class: "lbl" }, "已转化")
            ]),
            vue.createElementVNode("view", { class: "stat" }, [
              vue.createElementVNode(
                "text",
                { class: "num" },
                vue.toDisplayString($setup.stats.rewarded),
                1
                /* TEXT */
              ),
              vue.createElementVNode("text", { class: "lbl" }, "已发奖")
            ])
          ]),
          vue.createElementVNode("view", { class: "card col" }, [
            vue.createElementVNode("view", { class: "row" }, [
              vue.createElementVNode("text", { class: "sec-title" }, "我的积分奖励"),
              vue.createElementVNode(
                "text",
                { class: "pts" },
                vue.toDisplayString(((_a = $setup.points) == null ? void 0 : _a.total) || 0) + " 分 · " + vue.toDisplayString(((_b = $setup.points) == null ? void 0 : _b.level) || "见习红娘"),
                1
                /* TEXT */
              )
            ]),
            !$setup.referralLogs.length ? (vue.openBlock(), vue.createElementBlock("view", {
              key: 0,
              class: "hint"
            }, "暂无推荐成功奖励，邀请好友开通会员即得积分～")) : vue.createCommentVNode("v-if", true),
            (vue.openBlock(true), vue.createElementBlock(
              vue.Fragment,
              null,
              vue.renderList($setup.referralLogs, (l, i) => {
                return vue.openBlock(), vue.createElementBlock("view", {
                  key: i,
                  class: "log"
                }, [
                  vue.createElementVNode("view", { class: "log-l" }, [
                    vue.createElementVNode(
                      "text",
                      { class: "log-t" },
                      vue.toDisplayString(l.typeLabel || "推荐成功"),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "log-r" },
                      "+" + vue.toDisplayString(l.delta) + " 分",
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode(
                    "text",
                    { class: "log-time" },
                    vue.toDisplayString(l.createdAt || ""),
                    1
                    /* TEXT */
                  )
                ]);
              }),
              128
              /* KEYED_FRAGMENT */
            ))
          ]),
          vue.createElementVNode("view", { class: "card col" }, [
            vue.createElementVNode("view", { class: "sec-title" }, "推荐记录"),
            !$setup.referrals.length ? (vue.openBlock(), vue.createElementBlock("view", {
              key: 0,
              class: "hint"
            }, "还没有邀请记录，快去分享专属码吧～")) : vue.createCommentVNode("v-if", true),
            (vue.openBlock(true), vue.createElementBlock(
              vue.Fragment,
              null,
              vue.renderList($setup.referrals, (r) => {
                return vue.openBlock(), vue.createElementBlock("view", {
                  key: r.id,
                  class: "rec"
                }, [
                  vue.createElementVNode("view", { class: "rec-l" }, [
                    vue.createElementVNode(
                      "text",
                      { class: "rec-name" },
                      vue.toDisplayString(r.refereeName || $setup.maskPhone(r.refereePhone)),
                      1
                      /* TEXT */
                    ),
                    vue.createElementVNode(
                      "text",
                      { class: "rec-phone" },
                      vue.toDisplayString($setup.maskPhone(r.refereePhone)),
                      1
                      /* TEXT */
                    )
                  ]),
                  vue.createElementVNode(
                    "text",
                    {
                      class: vue.normalizeClass(["badge", $setup.statusClass(r.status)])
                    },
                    vue.toDisplayString(r.status),
                    3
                    /* TEXT, CLASS */
                  )
                ]);
              }),
              128
              /* KEYED_FRAGMENT */
            ))
          ])
        ],
        64
        /* STABLE_FRAGMENT */
      ))
    ]);
  }
  const PagesReferralIndex = /* @__PURE__ */ _export_sfc(_sfc_main$2, [["render", _sfc_render$1], ["__scopeId", "data-v-d493b880"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/referral/index.vue"]]);
  const _sfc_main$1 = /* @__PURE__ */ vue.defineComponent({
    __name: "register",
    setup(__props, { expose: __expose }) {
      __expose();
      const code = vue.ref("");
      const name = vue.ref("");
      const phone = vue.ref("");
      const submitting = vue.ref(false);
      onLoad((opts) => {
        if (opts == null ? void 0 : opts.code)
          code.value = String(opts.code);
      });
      async function submit() {
        if (!code.value) {
          uni.showToast({ title: "缺少推荐码", icon: "none" });
          return;
        }
        if (!/^1[3-9]\d{9}$/.test(phone.value || "")) {
          uni.showToast({ title: "请输入正确的手机号", icon: "none" });
          return;
        }
        submitting.value = true;
        try {
          uni.showLoading({ title: "提交中…", mask: true });
          await memberApi.registerByCode({ code: code.value, refereeName: name.value, refereePhone: phone.value });
          const login = await memberApi.login({ phone: phone.value });
          memberLoginAndStore(login);
          uni.hideLoading();
          uni.showToast({ title: "注册成功，去开通会员吧", icon: "success" });
          setTimeout(() => uni.reLaunch({ url: "/pages/member-portal/home" }), 900);
        } catch (e) {
          uni.hideLoading();
          uni.showToast({ title: errMsg(e), icon: "none" });
        } finally {
          submitting.value = false;
        }
      }
      const __returned__ = { code, name, phone, submitting, submit };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  function _sfc_render(_ctx, _cache, $props, $setup, $data, $options) {
    return vue.openBlock(), vue.createElementBlock("view", { class: "page" }, [
      vue.createElementVNode("view", { class: "hero" }, [
        vue.createElementVNode("text", { class: "title" }, "好友邀请你加入鹊桥"),
        vue.createElementVNode("text", { class: "sub" }, "完善信息注册会员，开启你的专属缘分")
      ]),
      vue.createElementVNode("view", { class: "card col" }, [
        vue.createElementVNode("view", { class: "field" }, [
          vue.createElementVNode("text", { class: "lbl" }, "推荐码"),
          vue.createElementVNode("input", {
            class: "inp",
            value: $setup.code,
            disabled: "",
            placeholder: "由邀请人分享"
          }, null, 8, ["value"])
        ]),
        vue.createElementVNode("view", { class: "field" }, [
          vue.createElementVNode("text", { class: "lbl" }, "昵称"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              class: "inp",
              "onUpdate:modelValue": _cache[0] || (_cache[0] = ($event) => $setup.name = $event),
              placeholder: "怎么称呼你"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.name]
          ])
        ]),
        vue.createElementVNode("view", { class: "field" }, [
          vue.createElementVNode("text", { class: "lbl" }, "手机号"),
          vue.withDirectives(vue.createElementVNode(
            "input",
            {
              class: "inp",
              "onUpdate:modelValue": _cache[1] || (_cache[1] = ($event) => $setup.phone = $event),
              type: "number",
              maxlength: "11",
              placeholder: "用于接收牵线通知"
            },
            null,
            512
            /* NEED_PATCH */
          ), [
            [vue.vModelText, $setup.phone]
          ])
        ]),
        vue.createElementVNode("button", {
          class: "submit",
          disabled: $setup.submitting,
          onClick: $setup.submit
        }, vue.toDisplayString($setup.submitting ? "提交中…" : "注册并领取专属服务"), 9, ["disabled"])
      ]),
      vue.createElementVNode("view", { class: "tip" }, "注册即表示同意《用户协议》与《隐私政策》，信息仅用于牵线匹配。")
    ]);
  }
  const PagesReferralRegister = /* @__PURE__ */ _export_sfc(_sfc_main$1, [["render", _sfc_render], ["__scopeId", "data-v-64edf09c"], ["__file", "D:/ai_project/queqiao-workbench/src/pages/referral/register.vue"]]);
  __definePage("pages/login/index", PagesLoginIndex);
  __definePage("pages/workspace/index", PagesWorkspaceIndex);
  __definePage("pages/members/index", PagesMembersIndex);
  __definePage("pages/match/index", PagesMatchIndex);
  __definePage("pages/message/index", PagesMessageIndex);
  __definePage("pages/profile/index", PagesProfileIndex);
  __definePage("pages/member/input", PagesMemberInput);
  __definePage("pages/member/detail", PagesMemberDetail);
  __definePage("pages/schedule/index", PagesScheduleIndex);
  __definePage("pages/followup/kanban", PagesFollowupKanban);
  __definePage("pages/member-portal/login", PagesMemberPortalLogin);
  __definePage("pages/member-portal/home", PagesMemberPortalHome);
  __definePage("pages/member-portal/match-detail", PagesMemberPortalMatchDetail);
  __definePage("pages/referral/index", PagesReferralIndex);
  __definePage("pages/referral/register", PagesReferralRegister);
  const _sfc_main = /* @__PURE__ */ vue.defineComponent({
    __name: "App",
    setup(__props, { expose: __expose }) {
      __expose();
      const guard = (args) => {
        if (!getToken() && args && typeof args.url === "string" && !args.url.includes("/pages/login/")) {
          uni.reLaunch({ url: "/pages/login/index" });
          return false;
        }
        return true;
      };
      onLaunch(() => {
        formatAppLog("log", "at App.vue:15", "鹊桥 · 红娘工作台 launched");
        uni.addInterceptor("navigateTo", { invoke: guard });
        uni.addInterceptor("redirectTo", { invoke: guard });
        if (!getToken()) {
          uni.reLaunch({ url: "/pages/login/index" });
        }
      });
      onShow(() => {
        formatAppLog("log", "at App.vue:23", "App Show");
      });
      onHide(() => {
        formatAppLog("log", "at App.vue:26", "App Hide");
      });
      const __returned__ = { guard, get onLaunch() {
        return onLaunch;
      }, get onShow() {
        return onShow;
      }, get onHide() {
        return onHide;
      }, get getToken() {
        return getToken;
      } };
      Object.defineProperty(__returned__, "__isScriptSetup", { enumerable: false, value: true });
      return __returned__;
    }
  });
  const App = /* @__PURE__ */ _export_sfc(_sfc_main, [["__file", "D:/ai_project/queqiao-workbench/src/App.vue"]]);
  const LT = {
    Launch: "1",
    Hide: "3",
    Page: "11",
    Event: "21",
    Error: "31",
    Push: "101"
  };
  const CST = {
    ColdLaunch: 1,
    BackgroundTimeout: 2,
    PageInactiveTimeout: 3
  };
  const IEY = {
    No: 0,
    Yes: 1
  };
  function toIey(input) {
    if (input === true || input === 1 || input === "1")
      return IEY.Yes;
    return IEY.No;
  }
  function __awaiter(thisArg, _arguments, P, generator) {
    function adopt(value) {
      return value instanceof P ? value : new P(function(resolve) {
        resolve(value);
      });
    }
    return new (P || (P = Promise))(function(resolve, reject) {
      function fulfilled(value) {
        try {
          step(generator.next(value));
        } catch (e) {
          reject(e);
        }
      }
      function rejected(value) {
        try {
          step(generator["throw"](value));
        } catch (e) {
          reject(e);
        }
      }
      function step(result) {
        result.done ? resolve(result.value) : adopt(result.value).then(fulfilled, rejected);
      }
      step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
  }
  typeof SuppressedError === "function" ? SuppressedError : function(error, suppressed, message) {
    var e = new Error(message);
    return e.name = "SuppressedError", e.error = error, e.suppressed = suppressed, e;
  };
  const DEFAULT_MAX_LENGTH = 4096;
  const TRUNCATED_SUFFIX = "…[truncated]";
  function safeStringify(value, max = DEFAULT_MAX_LENGTH) {
    var _a;
    if (value === void 0)
      return "";
    let raw;
    if (typeof value === "string") {
      raw = value;
    } else {
      const seen = /* @__PURE__ */ new WeakSet();
      try {
        raw = (_a = JSON.stringify(value, (_key, val) => {
          if (typeof val === "object" && val !== null) {
            if (seen.has(val))
              return "[Circular]";
            seen.add(val);
          }
          if (typeof val === "bigint")
            return val.toString();
          if (typeof val === "function")
            return `[Function ${val.name || "anonymous"}]`;
          return val;
        })) !== null && _a !== void 0 ? _a : "";
      } catch (e) {
        raw = `[Unserializable: ${e.message}]`;
      }
    }
    if (raw.length > max) {
      return raw.slice(0, Math.max(0, max - TRUNCATED_SUFFIX.length)) + TRUNCATED_SUFFIX;
    }
    return raw;
  }
  function tryRun(fn, fallback) {
    try {
      return fn();
    } catch (_a) {
      return fallback;
    }
  }
  function withRetry(fn, opts) {
    return __awaiter(this, void 0, void 0, function* () {
      var _a;
      const total = Math.max(1, Math.floor(opts.times));
      const sleep = (_a = opts.sleep) !== null && _a !== void 0 ? _a : defaultSleep;
      let lastErr;
      for (let attempt = 1; attempt <= total; attempt++) {
        try {
          return yield fn();
        } catch (e) {
          lastErr = e;
          if (attempt >= total)
            break;
          yield sleep(opts.baseDelayMs * Math.pow(2, attempt - 1));
        }
      }
      throw lastErr;
    });
  }
  function defaultSleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }
  function isUsableUniRuntime(candidate) {
    if (candidate == null || typeof candidate !== "object")
      return false;
    const u = candidate;
    return typeof u.getStorageSync === "function" || typeof u.onCreateVueApp === "function" || typeof u.request === "function" || typeof u.onAppShow === "function";
  }
  function getModuleUniCandidate() {
    if (typeof uni === "undefined" || uni == null || typeof uni !== "object") {
      return void 0;
    }
    return uni;
  }
  function getWindowObject() {
    try {
      const w = Function('return typeof window !== "undefined" ? window : undefined')();
      return w != null ? w : void 0;
    } catch (_a) {
      return void 0;
    }
  }
  function getGlobalObject() {
    if (typeof globalThis !== "undefined" && globalThis != null) {
      return globalThis;
    }
    if (typeof global !== "undefined" && global != null) {
      return global;
    }
    if (typeof self !== "undefined" && self != null) {
      return self;
    }
    const win = getWindowObject();
    if (win)
      return win;
    return {};
  }
  function buildInjectedUniRuntime() {
    try {
      const out = {};
      const pick = (name, fn) => {
        if (typeof fn === "function")
          out[name] = fn;
      };
      pick("getStorageSync", uni.getStorageSync);
      pick("setStorageSync", uni.setStorageSync);
      pick("removeStorageSync", uni.removeStorageSync);
      pick("getSystemInfoSync", uni.getSystemInfoSync);
      pick("getDeviceInfo", uni.getDeviceInfo);
      pick("getAppBaseInfo", uni.getAppBaseInfo);
      pick("getWindowInfo", uni.getWindowInfo);
      pick("getNetworkType", uni.getNetworkType);
      pick("request", uni.request);
      pick("onAppShow", uni.onAppShow);
      pick("offAppShow", uni.offAppShow);
      pick("onAppHide", uni.onAppHide);
      pick("offAppHide", uni.offAppHide);
      pick("onAppLaunch", uni.onAppLaunch);
      pick("offAppLaunch", uni.offAppLaunch);
      pick("getLaunchOptionsSync", uni.getLaunchOptionsSync);
      pick("addInterceptor", uni.addInterceptor);
      pick("removeInterceptor", uni.removeInterceptor);
      pick("getPushClientId", uni.getPushClientId);
      pick("getAccountInfoSync", uni.getAccountInfoSync);
      pick("onCreateVueApp", uni.onCreateVueApp);
      return Object.keys(out).length > 0 ? out : void 0;
    } catch (_e) {
      return void 0;
    }
  }
  function probeUniRuntime() {
    const globalThisAvailable = typeof globalThis !== "undefined";
    const g = getGlobalObject();
    const globalUni = g.uni;
    const globalThisHasUni = globalUni != null && typeof globalUni === "object";
    const globalThisUniStub = globalThisHasUni && !isUsableUniRuntime(globalUni);
    const moduleUni = getModuleUniCandidate();
    const moduleUniDefined = moduleUni != null;
    if (isUsableUniRuntime(globalUni)) {
      return {
        resolved: true,
        source: "globalThis",
        globalThisHasUni: true,
        globalThisUniStub: false,
        moduleUniDefined,
        globalThisAvailable,
        uni: globalUni
      };
    }
    if (isUsableUniRuntime(moduleUni)) {
      return {
        resolved: true,
        source: "module",
        globalThisHasUni,
        globalThisUniStub,
        moduleUniDefined: true,
        globalThisAvailable,
        uni: moduleUni
      };
    }
    const injectedUni = buildInjectedUniRuntime();
    if (isUsableUniRuntime(injectedUni)) {
      return {
        resolved: true,
        source: "injected",
        globalThisHasUni,
        globalThisUniStub,
        moduleUniDefined,
        globalThisAvailable,
        uni: injectedUni
      };
    }
    return {
      resolved: false,
      source: "none",
      globalThisHasUni,
      globalThisUniStub,
      moduleUniDefined,
      globalThisAvailable,
      uni: void 0
    };
  }
  function resolveUniRuntime() {
    const probe = probeUniRuntime();
    return probe.resolved ? probe.uni : void 0;
  }
  const TAG = "[uni统计 2.0]";
  let runtimeDebug;
  let muteNonDebug;
  function preferSingleLineConsole() {
    return isAndroidOrIosRuntime();
  }
  function isAndroidOrIosRuntime() {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    const raw = (_a = "app") !== null && _a !== void 0 ? _a : "";
    const g = getGlobalObject();
    if (raw === "app" || raw === "app-plus" || raw === "app-harmony") {
      const n2 = (_d = (_c = (_b = g.plus) === null || _b === void 0 ? void 0 : _b.os) === null || _c === void 0 ? void 0 : _c.name) === null || _d === void 0 ? void 0 : _d.toLowerCase();
      if (!n2)
        return false;
      if (n2.includes("android"))
        return true;
      if (n2 === "ios" || n2.includes("iphone"))
        return true;
      return false;
    }
    if (raw.startsWith("mp-")) {
      try {
        const p = (_h = (_g = (_f = (_e = g.uni) === null || _e === void 0 ? void 0 : _e.getSystemInfoSync) === null || _f === void 0 ? void 0 : _f.call(_e)) === null || _g === void 0 ? void 0 : _g.platform) === null || _h === void 0 ? void 0 : _h.toLowerCase();
        return p === "android" || p === "ios";
      } catch (_j) {
        return false;
      }
    }
    return false;
  }
  function stringifyObjectArgForNative(value) {
    if (value === null || value === void 0)
      return value;
    if (typeof value !== "object")
      return value;
    if (value instanceof Error)
      return `${value.name}: ${value.message}`;
    return safeStringify(value);
  }
  function formatLogArgForNativeConsole(value) {
    if (value === null)
      return "null";
    if (value === void 0)
      return "undefined";
    if (typeof value === "string")
      return value;
    if (typeof value === "number" || typeof value === "boolean") {
      return String(value);
    }
    if (typeof value === "bigint")
      return String(value);
    if (typeof value === "symbol") {
      try {
        return value.toString();
      } catch (_a) {
        return "?";
      }
    }
    if (typeof value === "function") {
      const fn = value;
      return `[Function ${fn.name || "anonymous"}]`;
    }
    if (typeof value === "object") {
      if (value instanceof Error)
        return `${value.name}: ${value.message}`;
      return safeStringify(value);
    }
    return String(value);
  }
  function isNonDebugMuted() {
    if (muteNonDebug !== void 0)
      return muteNonDebug;
    return false;
  }
  function setMuteNonDebug(value) {
    muteNonDebug = value;
  }
  function emitConsole(method, args) {
    if (method !== "log" && isNonDebugMuted())
      return;
    const fn = console[method];
    if (!preferSingleLineConsole()) {
      fn.call(console, TAG, ...args);
      return;
    }
    const mapped = isAndroidOrIosRuntime() ? args.map(stringifyObjectArgForNative) : args;
    if (mapped.length === 0) {
      fn.call(console, TAG);
      return;
    }
    const body = mapped.map(formatLogArgForNativeConsole).join(" ");
    fn.call(console, `${TAG} ${body}`);
  }
  function isDebug() {
    if (runtimeDebug !== void 0)
      return runtimeDebug;
    const v = "false";
    return v === true;
  }
  function setDebug(value) {
    runtimeDebug = value;
  }
  const logger = {
    debug(...args) {
      if (!isDebug())
        return;
      emitConsole("log", args);
    },
    info(...args) {
      emitConsole("info", args);
    },
    warn(...args) {
      emitConsole("warn", args);
    },
    error(...args) {
      emitConsole("error", args);
    },
    setDebug,
    isDebug,
    setMuteNonDebug
  };
  const NAMESPACE_ROOT = "UNI_STAT_DATA";
  const LEGACY_NAMESPACE_ROOT = "$$STAT__DBDATA";
  const cache = /* @__PURE__ */ new Map();
  const knownKeys = /* @__PURE__ */ new Set();
  function fullKey(key) {
    const appid = "default";
    return `${NAMESPACE_ROOT}:${appid}:${key}`;
  }
  function getUni$a() {
    const raw = resolveUniRuntime();
    const u = raw != null && typeof raw === "object" ? raw : void 0;
    if (!u || typeof u.getStorageSync !== "function") {
      throw new Error("[uni统计 2.0] uni storage API is not available");
    }
    return u;
  }
  function get(key) {
    const fk = fullKey(key);
    if (cache.has(fk))
      return cache.get(fk);
    try {
      const raw = getUni$a().getStorageSync(fk);
      if (raw === "" || raw === null || raw === void 0) {
        cache.set(fk, void 0);
        return void 0;
      }
      cache.set(fk, raw);
      knownKeys.add(fk);
      return raw;
    } catch (_a) {
      return void 0;
    }
  }
  function safeRead(key) {
    const fk = fullKey(key);
    if (cache.has(fk))
      return { ok: true, value: cache.get(fk) };
    try {
      const raw = getUni$a().getStorageSync(fk);
      if (raw === "" || raw === null || raw === void 0) {
        cache.set(fk, void 0);
        return { ok: true, value: void 0 };
      }
      cache.set(fk, raw);
      knownKeys.add(fk);
      return { ok: true, value: raw };
    } catch (_a) {
      return { ok: false, value: void 0 };
    }
  }
  function set(key, value) {
    const fk = fullKey(key);
    if (value === void 0) {
      remove(key);
      return;
    }
    cache.set(fk, value);
    knownKeys.add(fk);
    try {
      getUni$a().setStorageSync(fk, value);
    } catch (_a) {
    }
  }
  function remove(key) {
    const fk = fullKey(key);
    cache.set(fk, void 0);
    try {
      getUni$a().removeStorageSync(fk);
    } catch (_a) {
    }
  }
  function batchGet(keys) {
    const out = {};
    for (const k of keys)
      out[k] = get(k);
    return out;
  }
  function batchSet(entries) {
    for (const k of Object.keys(entries))
      set(k, entries[k]);
  }
  function clearNamespace() {
    let uni2;
    try {
      uni2 = getUni$a();
    } catch (_a) {
    }
    for (const fk of Array.from(knownKeys)) {
      try {
        uni2 === null || uni2 === void 0 ? void 0 : uni2.removeStorageSync(fk);
      } catch (_b) {
      }
      cache.set(fk, void 0);
    }
    knownKeys.clear();
  }
  function __resetCache() {
    cache.clear();
    knownKeys.clear();
  }
  const storage = {
    get,
    set,
    remove,
    safeRead,
    batchGet,
    batchSet,
    clearNamespace,
    __resetCache
  };
  const KEY_FVTS = "visit:fvts";
  const KEY_LVTS = "visit:lvts";
  const KEY_TVC = "visit:tvc";
  const EMPTY_SNAPSHOT = {
    fvts: 0,
    lvts: 0,
    tvc: 0,
    isNewUser: true,
    degraded: false
  };
  let loaded = null;
  let pending = null;
  let pendingRenewal = null;
  let committed = null;
  let lastBuilt = null;
  let buildCalledInProcess = false;
  function toNum(v) {
    if (typeof v === "number" && Number.isFinite(v) && v >= 0)
      return v;
    if (typeof v === "string" && v.length > 0) {
      const n2 = Number(v);
      if (Number.isFinite(n2) && n2 >= 0)
        return n2;
    }
    return 0;
  }
  function isLikelyFreshDevice(snap) {
    return snap.fvts === 0 && snap.lvts === 0 && snap.tvc === 0;
  }
  function isTrustworthyNewUser(snap) {
    if (!snap.isNewUser)
      return false;
    return !snap.degraded || isLikelyFreshDevice(snap);
  }
  function loadVisitSnapshot() {
    const fvtsR = storage.safeRead(KEY_FVTS);
    const lvtsR = storage.safeRead(KEY_LVTS);
    const tvcR = storage.safeRead(KEY_TVC);
    const degraded = !fvtsR.ok || !lvtsR.ok || !tvcR.ok;
    const fvts = toNum(fvtsR.value);
    const lvts = toNum(lvtsR.value);
    const tvc = toNum(tvcR.value);
    const snapshot = {
      fvts,
      lvts,
      tvc,
      isNewUser: lvts === 0,
      degraded
    };
    if (degraded) {
      const likelyFresh = fvts === 0 && lvts === 0 && tvc === 0 && snapshot.isNewUser;
      if (!likelyFresh) {
        logger.warn("[uni统计 2.0] visit snapshot degraded; some storage keys read failed");
      }
    }
    loaded = snapshot;
    return snapshot;
  }
  function ensureLoaded() {
    if (!loaded)
      loaded = EMPTY_SNAPSHOT;
    return loaded;
  }
  function persistNewUserBaseline(now) {
    storage.set(KEY_FVTS, now);
    storage.set(KEY_LVTS, now);
    storage.set(KEY_TVC, 1);
    const baseline = {
      fvts: now,
      lvts: now,
      tvc: 1,
      isNewUser: false,
      degraded: false
    };
    loaded = baseline;
    committed = baseline;
  }
  function buildVisitFields(now) {
    const snap = ensureLoaded();
    if (buildCalledInProcess && lastBuilt) {
      logger.warn("[uni统计 2.0] buildVisitFields() called twice in same process; returning cached fields");
      return Object.assign({}, lastBuilt);
    }
    buildCalledInProcess = true;
    if (isTrustworthyNewUser(snap)) {
      pending = { fvts: now, lvts: 0, tvc: 1, now };
      persistNewUserBaseline(now);
    } else if (snap.isNewUser) {
      logger.warn("[uni统计 2.0] visit degraded: lvts 读取失败但检测到历史数据，按老用户处理以避免新增虚高");
      const fvts = snap.fvts > 0 ? snap.fvts : now;
      pending = { fvts, lvts: fvts, tvc: snap.tvc + 1, now };
    } else {
      pending = {
        fvts: snap.fvts,
        lvts: snap.lvts,
        tvc: snap.tvc + 1,
        now
      };
    }
    lastBuilt = { fvts: pending.fvts, lvts: pending.lvts, tvc: pending.tvc };
    return Object.assign({}, lastBuilt);
  }
  function buildVisitFieldsForSessionRenewal(now) {
    let fvts;
    let lvts;
    let tvc;
    if (committed) {
      fvts = committed.fvts;
      lvts = committed.lvts;
      tvc = committed.tvc + 1;
    } else if (lastBuilt) {
      fvts = lastBuilt.fvts;
      lvts = lastBuilt.lvts !== 0 ? lastBuilt.lvts : lastBuilt.fvts;
      tvc = lastBuilt.tvc;
    } else {
      const snap = ensureLoaded();
      if (isTrustworthyNewUser(snap)) {
        fvts = now;
        lvts = 0;
        tvc = 1;
        persistNewUserBaseline(now);
      } else if (snap.isNewUser) {
        fvts = snap.fvts > 0 ? snap.fvts : now;
        lvts = fvts;
        tvc = snap.tvc + 1;
      } else {
        fvts = snap.fvts;
        lvts = snap.lvts;
        tvc = snap.tvc + 1;
      }
    }
    pendingRenewal = { fvts, lvts, tvc, now };
    return { fvts, lvts, tvc };
  }
  function commitVisitOnAck(now) {
    if (pending) {
      const snap = ensureLoaded();
      const newFvts2 = snap.fvts === 0 ? now : snap.fvts;
      const newLvts2 = now;
      const newTvc2 = pending.tvc;
      storage.set(KEY_FVTS, newFvts2);
      storage.set(KEY_LVTS, newLvts2);
      storage.set(KEY_TVC, newTvc2);
      committed = {
        fvts: newFvts2,
        lvts: newLvts2,
        tvc: newTvc2,
        isNewUser: false,
        degraded: false
      };
      loaded = committed;
      pending = null;
      return;
    }
    if (!pendingRenewal)
      return;
    const newFvts = pendingRenewal.fvts;
    const newLvts = now;
    const newTvc = pendingRenewal.tvc;
    storage.set(KEY_FVTS, newFvts);
    storage.set(KEY_LVTS, newLvts);
    storage.set(KEY_TVC, newTvc);
    committed = {
      fvts: newFvts,
      lvts: newLvts,
      tvc: newTvc,
      isNewUser: false,
      degraded: false
    };
    loaded = committed;
    pendingRenewal = null;
  }
  function rollbackPendingVisit() {
    pending = null;
    pendingRenewal = null;
  }
  const KEY_ENTRY = "session:entryRoute";
  let cached$3;
  let entryDeparted = false;
  function markEntryPage(route) {
    if (!route)
      return;
    const existing = getEntryRoute();
    if (existing)
      return;
    storage.set(KEY_ENTRY, route);
    cached$3 = route;
  }
  function getEntryRoute() {
    if (cached$3 !== void 0)
      return cached$3 || void 0;
    const r = storage.safeRead(KEY_ENTRY);
    if (!r.ok)
      return void 0;
    if (typeof r.value === "string" && r.value.length > 0) {
      cached$3 = r.value;
      return r.value;
    }
    cached$3 = "";
    return void 0;
  }
  function isEntry(route) {
    if (!route)
      return false;
    const entry = getEntryRoute();
    return entry === route;
  }
  function isEntryForIey(route) {
    if (entryDeparted)
      return false;
    return isEntry(route);
  }
  function markEntryDeparted() {
    entryDeparted = true;
  }
  function clearEntry() {
    cached$3 = "";
    entryDeparted = false;
    storage.remove(KEY_ENTRY);
  }
  let titleMapCache;
  function getVue3TitleMap() {
    if (titleMapCache)
      return titleMapCache;
    titleMapCache = {};
    try {
      const raw = '{"pages/login/index":"登录","pages/workspace/index":"工作台","pages/members/index":"会员库","pages/match/index":"智能牵线","pages/message/index":"消息中心","pages/profile/index":"我的","pages/member/input":"智能录入与编辑","pages/member/detail":"会员资料","pages/schedule/index":"约见日程","pages/followup/kanban":"牵线跟进看板","pages/member-portal/login":"会员登录","pages/member-portal/home":"会员中心","pages/member-portal/match-detail":"牵线详情"}';
      if (typeof raw !== "string" || !raw)
        ;
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
        titleMapCache = parsed;
      }
    } catch (_a) {
      titleMapCache = {};
    }
    return titleMapCache;
  }
  function getTitleMap() {
    let map = {};
    map = getVue3TitleMap();
    return map;
  }
  function getPagesJsonNavigationTitle(routePath) {
    if (!routePath || typeof routePath !== "string")
      return "";
    const pathOnly = routePath.split("?")[0].trim();
    if (!pathOnly)
      return "";
    const map = getTitleMap();
    let result = "";
    const keys = [pathOnly];
    if (pathOnly.startsWith("/")) {
      keys.push(pathOnly.slice(1));
    } else {
      keys.push(`/${pathOnly}`);
    }
    for (const k of keys) {
      const v = map[k];
      if (typeof v === "string" && v.length > 0) {
        result = v;
        break;
      }
    }
    return result;
  }
  const state$2 = { page: "", config: "", report: "" };
  function setPageTitle(title) {
    state$2.page = typeof title === "string" ? title : "";
  }
  function setConfigTitle(title) {
    state$2.config = typeof title === "string" ? title : "";
  }
  function setReportTitle(title) {
    state$2.report = typeof title === "string" ? title : "";
  }
  function getCurrentTitle() {
    return { ttn: state$2.page, ttpj: state$2.config, ttc: state$2.report };
  }
  function clearPageTitle() {
    state$2.page = "";
  }
  function nowMs() {
    return Date.now();
  }
  function nowSec() {
    return Math.floor(Date.now() / 1e3);
  }
  function clampUrlrefStaySec(deltaSec) {
    const d = deltaSec > 0 ? deltaSec : 0;
    return d < 1 ? 1 : d;
  }
  function normalizeStatOsP(info) {
    var _a, _b, _c, _d, _e;
    const fromToken = (raw) => {
      const s2 = raw.toLowerCase().trim();
      if (!s2)
        return "";
      if (s2 === "devtools")
        return "";
      if (s2 === "android")
        return "android";
      if (s2 === "ios" || s2 === "iphone")
        return "ios";
      if (s2.includes("android"))
        return "android";
      if (s2.includes("iphone") || s2 === "iphone os" || /\bios\b/.test(s2))
        return "ios";
      if (s2.includes("harmony") || s2 === "ohos" || s2 === "openharmony")
        return "harmonyos";
      if (s2.includes("windows") || s2 === "windows_nt")
        return "windows";
      if (s2 === "mac" || s2 === "darwin" || s2.includes("mac os") || s2 === "macos")
        return "macos";
      if (s2.includes("linux") && !s2.includes("android"))
        return "linux";
      return "";
    };
    const p0 = fromToken((_a = info.platform) !== null && _a !== void 0 ? _a : "");
    if (p0)
      return p0;
    const p1 = fromToken((_b = info.osName) !== null && _b !== void 0 ? _b : "");
    if (p1)
      return p1;
    const sys = ((_c = info.system) !== null && _c !== void 0 ? _c : "").toLowerCase();
    if (sys.includes("android"))
      return "android";
    if (sys.includes("iphone") || /\bios\b/.test(sys))
      return "ios";
    if (sys.includes("harmony") || sys.includes("ohos"))
      return "harmonyos";
    if (sys.includes("windows"))
      return "windows";
    if (sys.includes("mac os") || sys.includes("darwin"))
      return "macos";
    if (sys.includes("linux"))
      return "linux";
    const plus2 = getGlobalObject().plus;
    const p2 = fromToken((_e = (_d = plus2 === null || plus2 === void 0 ? void 0 : plus2.os) === null || _d === void 0 ? void 0 : _d.name) !== null && _e !== void 0 ? _e : "");
    if (p2)
      return p2;
    return "";
  }
  function uniPlatformMpAliRaw() {
    const parts = ["y", "a", "p", "mp-ali"];
    return [...parts].reverse().join("");
  }
  const PLATFORM_MAP = {
    app: "n",
    "app-plus": "n",
    "app-harmony": "n",
    "mp-harmony": "mhm",
    h5: "h5",
    "mp-weixin": "wx",
    [uniPlatformMpAliRaw()]: "ali",
    "mp-baidu": "bd",
    "mp-toutiao": "tt",
    "mp-qq": "qq",
    "mp-kuaishou": "ks",
    "mp-lark": "lark",
    "mp-xhs": "xhs",
    "mp-jd": "jd",
    "quickapp-native": "qn",
    "quickapp-webview": "qw"
  };
  function getRawPlatform() {
    var _a;
    return (_a = "app") !== null && _a !== void 0 ? _a : "";
  }
  function getPlatform() {
    var _a;
    const raw = getRawPlatform();
    const mapped = PLATFORM_MAP[raw];
    if (!mapped)
      return "unknown";
    if (mapped === "ali") {
      const my = getGlobalObject().my;
      if (((_a = my === null || my === void 0 ? void 0 : my.env) === null || _a === void 0 ? void 0 : _a.clientName) === "dingtalk")
        return "dt";
      return "ali";
    }
    return mapped;
  }
  function isApp() {
    const raw = getRawPlatform();
    return raw === "app" || raw === "app-plus" || raw === "app-harmony";
  }
  function isMp() {
    return getRawPlatform().startsWith("mp-");
  }
  function isH5() {
    return getRawPlatform() === "h5";
  }
  function isNvue() {
    return Boolean(getGlobalObject().__NVUE__);
  }
  const STORAGE_KEY_UUID = "device:uuid";
  const WEB_UUID_KEY = "__DC_STAT_UUID";
  let cachedUuid = null;
  function preferGetDeviceInfoDeviceIdFirst() {
    if (isApp() || isH5())
      return true;
    return getRawPlatform() === "mp-weixin";
  }
  function readSysDeviceId() {
    const root = resolveUniRuntime();
    const u = root != null && typeof root === "object" ? root : void 0;
    if (!u || typeof u.getSystemInfoSync !== "function")
      return "";
    return tryRun(() => {
      var _a;
      return (_a = u.getSystemInfoSync().deviceId) !== null && _a !== void 0 ? _a : "";
    }, "");
  }
  function readGetDeviceInfoDeviceId() {
    const root = resolveUniRuntime();
    const u = root != null && typeof root === "object" ? root : void 0;
    if (!u || typeof u.getDeviceInfo !== "function")
      return "";
    return tryRun(() => {
      var _a;
      return (_a = u.getDeviceInfo().deviceId) !== null && _a !== void 0 ? _a : "";
    }, "");
  }
  function generateAnonUuid() {
    const ms = nowMs();
    const rnd = Math.floor(Math.random() * 1e6).toString().padStart(6, "0");
    return `${ms}${rnd}`;
  }
  function persistUuid(uuid) {
    tryRun(() => storage.set(STORAGE_KEY_UUID, uuid), void 0);
  }
  function getWebLocalStorage() {
    return tryRun(() => {
      const g = getGlobalObject();
      if (g.navigator && g.navigator.cookieEnabled === false)
        return void 0;
      const ls = g.localStorage;
      if (ls && typeof ls.getItem === "function" && typeof ls.setItem === "function") {
        return ls;
      }
      return void 0;
    }, void 0);
  }
  function readWebDeviceId() {
    const ls = getWebLocalStorage();
    if (!ls)
      return "";
    return tryRun(() => {
      const v = ls.getItem(WEB_UUID_KEY);
      return typeof v === "string" ? v : "";
    }, "");
  }
  function writeWebDeviceId(uuid) {
    const ls = getWebLocalStorage();
    if (!ls)
      return;
    tryRun(() => ls.setItem(WEB_UUID_KEY, uuid), void 0);
  }
  function resolveDeviceIdFromUni() {
    if (preferGetDeviceInfoDeviceIdFirst()) {
      const fromDeviceInfo = readGetDeviceInfoDeviceId();
      if (fromDeviceInfo)
        return fromDeviceInfo;
    }
    return readSysDeviceId();
  }
  function getUuid() {
    if (cachedUuid)
      return cachedUuid;
    if (isH5()) {
      const fromWeb = readWebDeviceId();
      if (fromWeb) {
        cachedUuid = fromWeb;
        return cachedUuid;
      }
    }
    const fromDevice = resolveDeviceIdFromUni();
    if (fromDevice) {
      persistUuid(fromDevice);
      if (isH5())
        writeWebDeviceId(fromDevice);
      cachedUuid = fromDevice;
      return cachedUuid;
    }
    const storedRead = storage.safeRead(STORAGE_KEY_UUID);
    if (storedRead.ok) {
      const stored = storedRead.value;
      if (typeof stored === "string" && stored.length > 0) {
        if (stored.startsWith("device-anon-")) {
          const upgraded = generateAnonUuid();
          persistUuid(upgraded);
          if (isH5())
            writeWebDeviceId(upgraded);
          cachedUuid = upgraded;
          return cachedUuid;
        }
        cachedUuid = stored;
        return cachedUuid;
      }
      const generated = generateAnonUuid();
      persistUuid(generated);
      if (isH5())
        writeWebDeviceId(generated);
      cachedUuid = generated;
      return cachedUuid;
    }
    const ephemeral = generateAnonUuid();
    if (isH5()) {
      writeWebDeviceId(ephemeral);
      cachedUuid = ephemeral;
      return cachedUuid;
    }
    return ephemeral;
  }
  const SUFFIX_HEAD_LEN = 8;
  const SUFFIX_TAIL_LEN = 4;
  function randomPart(len) {
    const r = Math.random().toString(36).slice(2, 2 + len);
    return r.length >= len ? r : r.padEnd(len, "0");
  }
  function sessionInstanceSuffix() {
    return `${randomPart(SUFFIX_HEAD_LEN)}-${randomPart(SUFFIX_TAIL_LEN)}`;
  }
  function anonNumericBody() {
    const ms = nowMs();
    const rnd = Math.floor(Math.random() * 1e6).toString().padStart(6, "0");
    return `${ms}${rnd}`;
  }
  function genSid(uuid) {
    if (uuid && uuid.length > 0) {
      return `${uuid}-${sessionInstanceSuffix()}`;
    }
    return `${anonNumericBody()}-${sessionInstanceSuffix()}`;
  }
  const KEY_SID = "session:id";
  const KEY_SST = "session:start";
  const KEY_SCT = "session:sct";
  const KEY_SEQ = "session:seq";
  const KEY_LAST_ACTIVE = "session:lastActive";
  const KEY_BG_TS = "session:bgTs";
  const KEY_LAST_SCENE = "session:lastScene";
  const DEFAULT_CONFIG = {
    backgroundTimeoutSec: 300,
    pageInactiveTimeoutSec: 1800
  };
  let config$1 = Object.assign({}, DEFAULT_CONFIG);
  let cached$2 = null;
  function configure$1(c) {
    config$1 = Object.assign({}, DEFAULT_CONFIG, c);
  }
  function readNum(key) {
    const r = storage.safeRead(key);
    if (!r.ok)
      return 0;
    const v = r.value;
    if (typeof v === "number" && Number.isFinite(v) && v >= 0)
      return v;
    if (typeof v === "string" && v.length > 0) {
      const n2 = Number(v);
      if (Number.isFinite(n2) && n2 >= 0)
        return n2;
    }
    return 0;
  }
  function readStr(key) {
    const r = storage.safeRead(key);
    if (!r.ok)
      return "";
    return typeof r.value === "string" ? r.value : "";
  }
  function elapsedNonNeg(now, from) {
    const diff = now - from;
    return diff > 0 ? diff : 0;
  }
  function loadFromStorage() {
    const sid = readStr(KEY_SID);
    if (!sid)
      return null;
    return {
      sid,
      sst: readNum(KEY_SST),
      sct: readNum(KEY_SCT) || CST.ColdLaunch,
      seq: readNum(KEY_SEQ),
      lastActive: readNum(KEY_LAST_ACTIVE),
      bgTs: readNum(KEY_BG_TS),
      lastScene: readStr(KEY_LAST_SCENE)
    };
  }
  function ensureCache() {
    if (cached$2 !== null)
      return cached$2;
    cached$2 = loadFromStorage();
    return cached$2;
  }
  function createNew(now, sct, scene) {
    const sid = genSid(getUuid());
    const next = {
      sid,
      sst: now,
      sct,
      seq: 0,
      lastActive: now,
      bgTs: 0,
      lastScene: scene
    };
    storage.set(KEY_SID, sid);
    storage.set(KEY_SST, now);
    storage.set(KEY_SCT, sct);
    storage.set(KEY_SEQ, 0);
    storage.set(KEY_LAST_ACTIVE, now);
    storage.set(KEY_BG_TS, 0);
    storage.set(KEY_LAST_SCENE, scene);
    cached$2 = next;
    return next;
  }
  function ensureSession(t, ctx) {
    const { now, scene = "" } = ctx;
    const snap = ensureCache();
    if (t === "cold_launch") {
      const created = createNew(now, CST.ColdLaunch, scene);
      return { snapshot: created, isNew: true, cst: CST.ColdLaunch };
    }
    if (!snap) {
      const created = createNew(now, CST.ColdLaunch, scene);
      return { snapshot: created, isNew: true, cst: CST.ColdLaunch };
    }
    if (t === "app_show") {
      const enterCandidates = [];
      if (ctx.backgroundEnteredAt && ctx.backgroundEnteredAt > 0) {
        enterCandidates.push(ctx.backgroundEnteredAt);
      }
      if (snap.bgTs > 0) {
        enterCandidates.push(snap.bgTs);
      }
      const enterTs = enterCandidates.length > 0 ? Math.min(...enterCandidates) : 0;
      const elapsed2 = enterTs > 0 ? elapsedNonNeg(now, enterTs) : elapsedNonNeg(now, snap.lastActive);
      const sceneChanged = !!scene && !!snap.lastScene && scene !== snap.lastScene;
      const fromBackground = enterTs > 0;
      if (sceneChanged || fromBackground && elapsed2 >= config$1.backgroundTimeoutSec) {
        const created = createNew(now, CST.BackgroundTimeout, scene);
        return { snapshot: created, isNew: true, cst: CST.BackgroundTimeout };
      }
      touch(now);
      storage.set(KEY_BG_TS, 0);
      if (cached$2)
        cached$2.bgTs = 0;
      return { snapshot: cached$2, isNew: false, cst: 0 };
    }
    if (t === "wx_scene_changed") {
      if (scene && scene !== snap.lastScene) {
        const created = createNew(now, CST.BackgroundTimeout, scene);
        return { snapshot: created, isNew: true, cst: CST.BackgroundTimeout };
      }
      return { snapshot: snap, isNew: false, cst: 0 };
    }
    const elapsed = elapsedNonNeg(now, snap.lastActive);
    if (elapsed >= config$1.pageInactiveTimeoutSec) {
      const created = createNew(now, CST.PageInactiveTimeout, scene || snap.lastScene);
      return { snapshot: created, isNew: true, cst: CST.PageInactiveTimeout };
    }
    touch(now);
    return { snapshot: cached$2, isNew: false, cst: 0 };
  }
  function markBackground(now) {
    if (!cached$2)
      cached$2 = loadFromStorage();
    if (!cached$2)
      return;
    storage.set(KEY_BG_TS, now);
    cached$2.bgTs = now;
  }
  function touch(now) {
    if (!cached$2)
      cached$2 = loadFromStorage();
    if (!cached$2)
      return;
    storage.set(KEY_LAST_ACTIVE, now);
    cached$2.lastActive = now;
  }
  function nextSeq() {
    if (!cached$2)
      cached$2 = loadFromStorage();
    if (!cached$2)
      return 0;
    const next = cached$2.seq + 1;
    cached$2.seq = next;
    storage.set(KEY_SEQ, next);
    return next;
  }
  function getSnapshot() {
    return ensureCache();
  }
  function syncLastScene(scene) {
    if (!scene)
      return;
    if (!cached$2)
      cached$2 = loadFromStorage();
    if (!cached$2)
      return;
    storage.set(KEY_LAST_SCENE, scene);
    cached$2.lastScene = scene;
  }
  function getPageVmType(vm) {
    var _a, _b, _c, _d, _e, _f, _g, _h;
    if (!vm)
      return null;
    const internalMpType = (_c = (_b = (_a = vm.$) === null || _a === void 0 ? void 0 : _a.type) === null || _b === void 0 ? void 0 : _b.mpType) !== null && _c !== void 0 ? _c : (_d = vm.type) === null || _d === void 0 ? void 0 : _d.mpType;
    if (vm.mpType === "page" || vm.$mpType === "page" || ((_e = vm.$mp) === null || _e === void 0 ? void 0 : _e.mpType) === "page" || ((_f = vm.$options) === null || _f === void 0 ? void 0 : _f.mpType) === "page" || internalMpType === "page") {
      return "page";
    }
    if (vm.mpType === "app" || vm.$mpType === "app" || ((_g = vm.$mp) === null || _g === void 0 ? void 0 : _g.mpType) === "app" || ((_h = vm.$options) === null || _h === void 0 ? void 0 : _h.mpType) === "app" || internalMpType === "app") {
      return "app";
    }
    return null;
  }
  function getTopPageVm() {
    var _a;
    const fn = getGlobalObject().getCurrentPages;
    if (typeof fn !== "function")
      return void 0;
    const pages = tryRun(() => fn(), []) || [];
    if (!Array.isArray(pages) || pages.length === 0)
      return void 0;
    const top = pages[pages.length - 1];
    return (_a = top === null || top === void 0 ? void 0 : top.$vm) !== null && _a !== void 0 ? _a : top;
  }
  function getCurrentRoute(pageVm) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l;
    const vm = pageVm !== null && pageVm !== void 0 ? pageVm : getTopPageVm();
    if (!vm)
      return "";
    if (getPlatform() === "bd") {
      const r = (_e = (_c = (_b = (_a = vm.$mp) === null || _a === void 0 ? void 0 : _a.page) === null || _b === void 0 ? void 0 : _b.is) !== null && _c !== void 0 ? _c : (_d = vm.$scope) === null || _d === void 0 ? void 0 : _d.is) !== null && _e !== void 0 ? _e : "";
      if (r)
        return r;
    }
    return (_l = (_h = (_f = vm.route) !== null && _f !== void 0 ? _f : (_g = vm.$scope) === null || _g === void 0 ? void 0 : _g.route) !== null && _h !== void 0 ? _h : (_k = (_j = vm.$mp) === null || _j === void 0 ? void 0 : _j.page) === null || _k === void 0 ? void 0 : _k.route) !== null && _l !== void 0 ? _l : "";
  }
  function getCurrentRouteWithQuery(pageVm) {
    var _a, _b;
    const vm = pageVm !== null && pageVm !== void 0 ? pageVm : getTopPageVm();
    if (!vm)
      return "";
    const page = (_a = vm.$page) !== null && _a !== void 0 ? _a : (_b = vm.$scope) === null || _b === void 0 ? void 0 : _b.$page;
    if (page) {
      if (page.fullPath && page.fullPath !== "/")
        return page.fullPath;
      if (page.route)
        return page.route;
    }
    return getCurrentRoute(vm);
  }
  function getUni$9() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  function getLaunchScene(override) {
    if (override !== void 0 && override !== null && override !== "") {
      return String(override);
    }
    const u = getUni$9();
    if (typeof (u === null || u === void 0 ? void 0 : u.getLaunchOptionsSync) !== "function")
      return "";
    if (!isMp())
      return "";
    return tryRun(() => {
      const opts = u.getLaunchOptionsSync();
      const scene = opts === null || opts === void 0 ? void 0 : opts.scene;
      return scene === void 0 || scene === null ? "" : String(scene);
    }, "");
  }
  function getUni$8() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  function getPushClientId(opts = {}) {
    const { enabled = false, timeoutMs = 3e3 } = opts;
    return new Promise((resolve) => {
      if (!enabled) {
        resolve({ ok: false, cid: "", reason: "disabled" });
        return;
      }
      const u = getUni$8();
      if (!u || typeof u.getPushClientId !== "function") {
        resolve({ ok: false, cid: "", reason: "unsupported" });
        return;
      }
      let settled = false;
      const finish = (r) => {
        if (settled)
          return;
        settled = true;
        resolve(r);
      };
      const timer = setTimeout(() => finish({ ok: false, cid: "", reason: "timeout" }), timeoutMs);
      tryRun(() => u.getPushClientId({
        success: (res) => {
          clearTimeout(timer);
          const cid = typeof (res === null || res === void 0 ? void 0 : res.cid) === "string" ? res.cid : "";
          if (!cid) {
            finish({ ok: false, cid: "", reason: "fail" });
            return;
          }
          finish({ ok: true, cid });
        },
        fail: () => {
          clearTimeout(timer);
          finish({ ok: false, cid: "", reason: "fail" });
        }
      }), void 0);
    });
  }
  const EMPTY_TITLE_SNAP = { ttn: "", ttpj: "", ttc: "" };
  const state$1 = {
    lastRoute: "",
    lastRouteFull: "",
    beforeLastRoute: "",
    beforeLastRouteFull: "",
    lastRouteEnterTime: 0,
    lastPageTitleSnap: Object.assign({}, EMPTY_TITLE_SNAP),
    lastIey: false,
    prevIey: false,
    isHide: false,
    wasBackgrounded: false,
    pendingBackgroundResume: false,
    backgroundEnteredAt: 0,
    suppressNextPageLogAfterResume: false,
    backgroundResumeLt1At: 0
  };
  const BACKGROUND_RESUME_DEBOUNCE_SEC = 1;
  const BACKGROUND_RESUME_LT1_DEDUP_SEC = 3;
  const PAGE_APP_HIDE_DEFER_MS = 120;
  let pageAppHideDeferTimer;
  function shouldEarlyConsumeBackgroundResumeInMixin() {
    return !shouldBindUniAppLifecycle();
  }
  function markBackgroundResumeLt1Emitted(now) {
    state$1.backgroundResumeLt1At = now;
  }
  function shouldSkipDuplicateBackgroundResumeLt1(now) {
    return state$1.backgroundResumeLt1At > 0 && now - state$1.backgroundResumeLt1At <= BACKGROUND_RESUME_LT1_DEDUP_SEC;
  }
  function cancelPageAppHideDefer() {
    if (pageAppHideDeferTimer !== void 0) {
      clearTimeout(pageAppHideDeferTimer);
      pageAppHideDeferTimer = void 0;
    }
  }
  function tryAppHideFromPageOnHideWhenH5Hidden(app, opts) {
    var _a;
    if (!isH5())
      return;
    if (state$1.pendingBackgroundResume)
      return;
    const vis = (_a = globalThis.document) === null || _a === void 0 ? void 0 : _a.visibilityState;
    if (vis === "hidden") {
      handleAppHide(app, opts);
    }
  }
  function tryAppHideFromPageOnHideWhenMpDefer(app, opts) {
    if (isH5())
      return;
    if (state$1.pendingBackgroundResume)
      return;
    cancelPageAppHideDefer();
    pageAppHideDeferTimer = setTimeout(() => {
      pageAppHideDeferTimer = void 0;
      if (state$1.pendingBackgroundResume)
        return;
      handleAppHide(app, opts);
    }, PAGE_APP_HIDE_DEFER_MS);
  }
  function tryVue3AppHideFromPageOnHide(app, opts) {
    if (state$1.pendingBackgroundResume)
      return;
    if (isH5()) {
      tryAppHideFromPageOnHideWhenH5Hidden(app, opts);
      return;
    }
    tryAppHideFromPageOnHideWhenMpDefer(app, opts);
  }
  function safeCollector(app) {
    return app.getCollector();
  }
  function normalizePathForEntryMark(raw) {
    var _a;
    if (!raw || typeof raw !== "string")
      return "";
    const noQuery = (_a = raw.split("?")[0]) !== null && _a !== void 0 ? _a : "";
    return noQuery.startsWith("/") ? noQuery.slice(1) : noQuery;
  }
  function reportNewSession(c, _cst, scene, now, attachVisit, url = "") {
    let visit;
    if (attachVisit && !firstVisitEmittedInProcess) {
      firstVisitEmittedInProcess = true;
      visit = tryRun(() => buildVisitFields(now), void 0);
    } else {
      visit = tryRun(() => buildVisitFieldsForSessionRenewal(now), void 0);
    }
    const payload = {
      lt: LT.Launch,
      t: now,
      sc: scene,
      visit
    };
    if (url)
      payload.url = url;
    c.report(payload);
  }
  let firstVisitEmittedInProcess = false;
  let titleSnapGeneration = 0;
  function scheduleDeferredTitleSnapshot() {
    const gen = titleSnapGeneration;
    const run = typeof queueMicrotask === "function" ? queueMicrotask : (fn) => {
      void Promise.resolve().then(fn);
    };
    run(() => {
      tryRun(() => {
        if (gen !== titleSnapGeneration)
          return;
        state$1.lastPageTitleSnap = Object.assign({}, getCurrentTitle());
      }, void 0);
    });
  }
  function handleLaunch(app, options = {}, opts = {}) {
    const c = safeCollector(app);
    if (!c)
      return;
    const now = nowSec();
    const scene = tryRun(() => getLaunchScene(options.scene), "");
    const result = tryRun(() => ensureSession("cold_launch", { now, scene }), null);
    if (!result)
      return;
    tryRun(() => clearEntry(), void 0);
    const url = options.path || "";
    const entryKey = normalizePathForEntryMark(url);
    if (entryKey) {
      tryRun(() => markEntryPage(entryKey), void 0);
    }
    reportNewSession(c, result.cst || CST.ColdLaunch, scene, now, true, url);
    if (opts.enablePush) {
      void getPushClientId({ enabled: true, timeoutMs: opts.pushTimeoutMs }).then((r) => {
        if (!r.ok || !r.cid)
          return;
        const c2 = safeCollector(app);
        if (!c2)
          return;
        c2.report({ lt: LT.Push, cid: r.cid, t: nowSec() });
      }).catch((e) => logger.warn("[uni统计 2.0] push cid fetch failed", e));
    }
  }
  function tryConsumeBackgroundResume(app, options = {}, _opts = {}, _from = "unknown") {
    if (!state$1.pendingBackgroundResume) {
      return false;
    }
    const bgEnterAt = state$1.backgroundEnteredAt;
    if (bgEnterAt <= 0) {
      return false;
    }
    const c = safeCollector(app);
    if (!c) {
      return false;
    }
    const now = nowSec();
    const elapsed = now - bgEnterAt;
    if (elapsed < BACKGROUND_RESUME_DEBOUNCE_SEC) {
      state$1.suppressNextPageLogAfterResume = true;
      return true;
    }
    state$1.wasBackgrounded = false;
    state$1.suppressNextPageLogAfterResume = true;
    state$1.lastRouteEnterTime = now;
    const scene = tryRun(() => getLaunchScene(options.scene), "");
    const result = tryRun(() => ensureSession("app_show", {
      now,
      scene,
      backgroundEnteredAt: bgEnterAt
    }), null);
    state$1.pendingBackgroundResume = false;
    state$1.backgroundEnteredAt = 0;
    if (!result || !result.isNew) {
      return true;
    }
    tryRun(() => clearEntry(), void 0);
    const url = options.path || state$1.lastRoute || "";
    const entryKey = normalizePathForEntryMark(url);
    if (entryKey) {
      tryRun(() => markEntryPage(entryKey), void 0);
    }
    reportNewSession(c, result.cst || CST.BackgroundTimeout, scene, now, false, url);
    markBackgroundResumeLt1Emitted(now);
    void c.flush(true).catch((e) => logger.warn("[uni统计 2.0] flush after new session (app_show) failed", e));
    return true;
  }
  function handleAppShow(app, options = {}, opts = {}) {
    if (tryConsumeBackgroundResume(app, options, opts, "handleAppShow"))
      return;
    const c = safeCollector(app);
    if (!c)
      return;
    const now = nowSec();
    const scene = tryRun(() => getLaunchScene(options.scene), "");
    if (shouldSkipDuplicateBackgroundResumeLt1(now)) {
      tryRun(() => syncLastScene(scene), void 0);
      return;
    }
    const result = tryRun(() => ensureSession("app_show", { now, scene }), null);
    if (!result || !result.isNew) {
      return;
    }
    tryRun(() => clearEntry(), void 0);
    const url = options.path || state$1.lastRoute || "";
    const entryKey = normalizePathForEntryMark(url);
    if (entryKey) {
      tryRun(() => markEntryPage(entryKey), void 0);
    }
    reportNewSession(c, result.cst || CST.BackgroundTimeout, scene, now, false, url);
    markBackgroundResumeLt1Emitted(now);
    void c.flush(true).catch((e) => logger.warn("[uni统计 2.0] flush after new session (app_show) failed", e));
  }
  function handleAppHide(app, opts = {}) {
    if (state$1.pendingBackgroundResume)
      return;
    const c = safeCollector(app);
    if (!c)
      return;
    const now = nowSec();
    state$1.wasBackgrounded = true;
    state$1.pendingBackgroundResume = true;
    state$1.backgroundEnteredAt = now;
    tryRun(() => markBackground(now), void 0);
    const deltaStay = state$1.lastRouteEnterTime > 0 ? now - state$1.lastRouteEnterTime : 0;
    const stayed = clampUrlrefStaySec(deltaStay);
    if (state$1.lastRoute && opts.enablePageLog !== false) {
      const exitedUrl = state$1.lastRouteFull || state$1.lastRoute;
      const ref = state$1.beforeLastRouteFull || state$1.beforeLastRoute || "";
      const snap = state$1.lastPageTitleSnap;
      const payload = {
        lt: LT.Page,
        t: now,
        url: exitedUrl,
        urlref_ts: stayed,
        iey: state$1.lastIey,
        ppiey: state$1.prevIey,
        ttn: snap.ttn,
        ttpj: snap.ttpj,
        ttc: snap.ttc
      };
      if (ref)
        payload.urlref = ref;
      c.report(payload);
      if (state$1.lastIey) {
        tryRun(() => markEntryDeparted(), void 0);
        state$1.lastIey = false;
      }
    }
    c.report({
      lt: LT.Hide,
      t: now,
      urlref: state$1.lastRoute,
      urlref_ts: stayed
    });
    void c.flush(true).catch((e) => logger.warn("[uni统计 2.0] flush on hide failed", e));
  }
  function handlePageShow(app, vm, opts = {}) {
    const c = safeCollector(app);
    if (!c)
      return;
    if (state$1.pendingBackgroundResume && shouldEarlyConsumeBackgroundResumeInMixin()) {
      tryConsumeBackgroundResume(app, {}, opts, "handlePageShow");
    }
    const now = nowSec();
    const route = tryRun(() => getCurrentRoute(vm), "");
    const url = tryRun(() => getCurrentRouteWithQuery(vm), "") || route;
    if (!route && !url)
      return;
    const result = tryRun(() => ensureSession("page_show", { now }), null);
    if (!result)
      return;
    tryRun(() => setReportTitle(""), void 0);
    tryRun(() => setConfigTitle(getPagesJsonNavigationTitle(route)), void 0);
    if (result.isNew) {
      tryRun(() => clearEntry(), void 0);
    }
    if (route) {
      tryRun(() => markEntryPage(route), void 0);
    }
    if (result.isNew) {
      reportNewSession(c, result.cst || CST.PageInactiveTimeout, "", now, false, url);
    }
    const shouldSuppressPageLog = state$1.suppressNextPageLogAfterResume;
    if (state$1.lastRoute && opts.enablePageLog !== false && !shouldSuppressPageLog) {
      const deltaStay = state$1.lastRouteEnterTime > 0 ? now - state$1.lastRouteEnterTime : 0;
      const stayed = clampUrlrefStaySec(deltaStay);
      const exitedUrl = state$1.lastRouteFull || state$1.lastRoute;
      const ref = state$1.beforeLastRouteFull || state$1.beforeLastRoute || "";
      const snap = state$1.lastPageTitleSnap;
      const payload = {
        lt: LT.Page,
        t: now,
        url: exitedUrl,
        urlref_ts: stayed,
        // 离开页是否入口页 / urlref 指向页是否入口页（进入新页前状态尚未被本轮覆盖）。
        iey: state$1.lastIey,
        ppiey: state$1.prevIey
      };
      if (ref)
        payload.urlref = ref;
      payload.ttn = snap.ttn;
      payload.ttpj = snap.ttpj;
      payload.ttc = snap.ttc;
      c.report(payload);
      if (state$1.lastIey) {
        tryRun(() => markEntryDeparted(), void 0);
      }
    }
    state$1.beforeLastRoute = state$1.lastRoute;
    state$1.beforeLastRouteFull = state$1.lastRouteFull;
    state$1.prevIey = state$1.lastIey;
    state$1.lastIey = !!route && tryRun(() => isEntryForIey(route), false);
    state$1.lastRoute = route;
    state$1.lastRouteFull = url;
    state$1.lastRouteEnterTime = now;
    state$1.suppressNextPageLogAfterResume = false;
    scheduleDeferredTitleSnapshot();
    state$1.isHide = false;
    if (result.isNew) {
      void c.flush(true).catch((e) => logger.warn("[uni统计 2.0] flush after new session (page_show) failed", e));
    }
  }
  function handlePageHide(app, _vm) {
    const c = safeCollector(app);
    if (!c)
      return;
    state$1.isHide = true;
    titleSnapGeneration++;
    state$1.lastPageTitleSnap = Object.assign({}, getCurrentTitle());
    tryRun(() => clearPageTitle(), void 0);
  }
  const rethrownErrors = typeof WeakSet === "function" ? /* @__PURE__ */ new WeakSet() : (
    // 极端环境降级：has=false 永不命中，add=noop；本模块只用 has/add 两个方法，
    // 其它方法（delete / [Symbol.toStringTag]）调用方不依赖，类型断言即可。
    {
      has: () => false,
      add: () => rethrownErrors
    }
  );
  function handleError(app, e) {
    const isObj = typeof e === "object" && e !== null;
    if (isObj && rethrownErrors.has(e))
      return;
    if (isObj)
      rethrownErrors.add(e);
    try {
      app.reportError(e);
    } catch (err) {
      logger.warn("[uni统计 2.0] handleError failed", err);
    }
    if (isMp()) {
      return;
    }
    tryRun(() => {
      setTimeout(() => {
        throw e;
      }, 0);
    }, void 0);
  }
  function getUni$7() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  function shouldMixinDispatchAppLifecycle() {
    let result = isH5() || getPlatform() === "n" || isNvue();
    result = isH5() || getPlatform() === "n" || isNvue();
    return result;
  }
  function shouldBindUniAppLifecycle() {
    let result = !isH5() && getPlatform() !== "n" && !isNvue();
    result = !isH5() && getPlatform() !== "n" && !isNvue();
    return result;
  }
  const uniAppHookRegistry = {
    showBound: false,
    hideBound: false,
    appShowCb: void 0,
    appHideCb: void 0
  };
  function tryBindUniAppLifecycle(app, opts = {}) {
    if (!shouldBindUniAppLifecycle())
      return false;
    const u = getUni$7();
    if (!u)
      return false;
    if (!uniAppHookRegistry.showBound && typeof u.onAppShow === "function") {
      uniAppHookRegistry.appShowCb = (e) => handleAppShow(app, e !== null && e !== void 0 ? e : {}, opts);
      tryRun(() => u.onAppShow(uniAppHookRegistry.appShowCb), void 0);
      uniAppHookRegistry.showBound = true;
    }
    if (!uniAppHookRegistry.hideBound && typeof u.onAppHide === "function") {
      uniAppHookRegistry.appHideCb = () => handleAppHide(app, opts);
      tryRun(() => u.onAppHide(uniAppHookRegistry.appHideCb), void 0);
      uniAppHookRegistry.hideBound = true;
    }
    return uniAppHookRegistry.showBound && uniAppHookRegistry.hideBound;
  }
  function unbindUniAppLifecycle() {
    if (!uniAppHookRegistry.showBound && !uniAppHookRegistry.hideBound)
      return;
    const cur = getUni$7();
    if (uniAppHookRegistry.showBound && uniAppHookRegistry.appShowCb && (cur === null || cur === void 0 ? void 0 : cur.offAppShow)) {
      tryRun(() => cur.offAppShow(uniAppHookRegistry.appShowCb), void 0);
    }
    if (uniAppHookRegistry.hideBound && uniAppHookRegistry.appHideCb && (cur === null || cur === void 0 ? void 0 : cur.offAppHide)) {
      tryRun(() => cur.offAppHide(uniAppHookRegistry.appHideCb), void 0);
    }
    uniAppHookRegistry.showBound = false;
    uniAppHookRegistry.hideBound = false;
    uniAppHookRegistry.appShowCb = void 0;
    uniAppHookRegistry.appHideCb = void 0;
  }
  function bindLifecycle(app, opts = {}) {
    let bound = true;
    const mixin = {
      onLaunch(options = {}) {
        handleLaunch(app, options, opts);
      },
      onLoad() {
      },
      onShow() {
        const vmType = getPageVmType(this);
        cancelPageAppHideDefer();
        if (state$1.pendingBackgroundResume && shouldEarlyConsumeBackgroundResumeInMixin()) {
          tryConsumeBackgroundResume(app, {}, opts, "mixin.onShow");
        }
        state$1.isHide = false;
        if (vmType === "page") {
          handlePageShow(app, this, opts);
        }
        if (shouldMixinDispatchAppLifecycle() && vmType === "app") {
          handleAppShow(app, {}, opts);
        }
      },
      onHide() {
        state$1.isHide = true;
        if (getPageVmType(this) === "page") {
          handlePageHide(app);
          tryVue3AppHideFromPageOnHide(app, opts);
        }
        if (shouldMixinDispatchAppLifecycle() && getPageVmType(this) === "app" && !state$1.pendingBackgroundResume) {
          handleAppHide(app, opts);
        }
      },
      onUnload() {
        if (state$1.isHide) {
          state$1.isHide = false;
          return;
        }
        handlePageHide(app);
      },
      onError(e) {
        handleError(app, e);
      }
    };
    if (shouldBindUniAppLifecycle()) {
      tryBindUniAppLifecycle(app, opts);
    }
    return {
      mixin,
      tryBindUniAppHooks: () => shouldBindUniAppLifecycle() && tryBindUniAppLifecycle(app, opts),
      unbind() {
        if (!bound)
          return;
        bound = false;
        unbindUniAppLifecycle();
      }
    };
  }
  const STAT_VERSION_PUBLIC = "5.23";
  const STAT_URL = "https://tongji.dcloud.io/uni/stat";
  const STAT_H5_URL = "https://tongji.dcloud.io/uni/stat.gif";
  const REPORT_INTERVAL_SEC = 10;
  const HTTP_MAX_RETRIES = 3;
  const CLOUD_MAX_RETRIES = 2;
  const IMAGE_MAX_RETRIES = 2;
  const RETRY_BASE_DELAY_MS = 1e3;
  const MP_WEIXIN_USE_PRELOAD_ASSETS_REPORT = true;
  const MP_WEIXIN_PRELOAD_TIMEOUT_MS = 3e4;
  const MP_WEIXIN_PRELOAD_FIRST_FLUSH_DELAY_MS = 2e3;
  const APP_CHANNEL_FIRST_FLUSH_DELAY_MS = 300;
  const SINGLE_EVENT_MAX_BYTES = 4 * 1024;
  const BATCH_REQUESTS_MAX_BYTES = 4 * 1024;
  const BATCH_MAX_EVENTS = 30;
  const QUEUE_MAX_EVENTS = 1e3;
  const RETRY_MAX_ATTEMPTS = 5;
  const IMAGE_REPORT_DEFAULTS = {
    host: "https://tongji-collector.dcloud.net.cn",
    /** 正式环境 */
    projectId: "964f0397-af5d-45bf-99d6-8fb3500d7849",
    topicId: "8563e231-f4cd-4ab0-8870-917e4b04e810"
    // 以下为历史测试环境（已停用，勿删便于回切排查）
    // projectId: '9fad19a2-b7f1-47f5-87ff-8621f545ab61',
    // topicId: '99b55c91-ed80-406e-b205-e9d18aca744d',
  };
  function getAppId$1() {
    var _a;
    return (_a = "") !== null && _a !== void 0 ? _a : "";
  }
  function assertCloudResultOk(res) {
    if (!res || typeof res !== "object")
      return;
    const r = res;
    if (r.success === false) {
      throw new Error("cloud receiver reported success=false");
    }
    if (typeof r.errCode === "number" && r.errCode !== 0) {
      throw new Error("cloud receiver reported errCode=" + String(r.errCode));
    }
  }
  function resolveSpace(injected) {
    if (injected)
      return injected;
    const raw = resolveUniRuntime();
    const u = raw != null && typeof raw === "object" ? raw : void 0;
    return u === null || u === void 0 ? void 0 : u.__stat_uniCloud_space;
  }
  function createCloudChannel(opts = {}) {
    var _a, _b;
    const receiverName = (_a = opts.receiverName) !== null && _a !== void 0 ? _a : "uni-stat-receiver";
    const maxRetries = (_b = opts.maxRetries) !== null && _b !== void 0 ? _b : CLOUD_MAX_RETRIES;
    function getReceiver() {
      const space = resolveSpace(opts.uniCloudSpace);
      if (!space || typeof space.importObject !== "function")
        return void 0;
      try {
        return space.importObject(receiverName, { customUI: true });
      } catch (e) {
        logger.warn("[uni统计 2.0] cloud importObject threw", e);
        return void 0;
      }
    }
    function once(payload) {
      const receiver = getReceiver();
      if (!receiver || typeof receiver.report !== "function") {
        return Promise.reject(new Error("uniCloud space unavailable"));
      }
      return Promise.resolve(receiver.report(payload)).then((res) => {
        assertCloudResultOk(res);
      });
    }
    return {
      name: "2.0",
      available() {
        const space = resolveSpace(opts.uniCloudSpace);
        return !!(space && typeof space.importObject === "function");
      },
      send(payload) {
        return __awaiter(this, void 0, void 0, function* () {
          try {
            yield withRetry(() => once(payload), {
              times: maxRetries,
              baseDelayMs: RETRY_BASE_DELAY_MS,
              sleep: opts.sleep
            });
          } catch (e) {
            logger.warn("[uni统计 2.0] 统计上报失败（云函数已重试）", e);
            throw e;
          }
        });
      }
    };
  }
  function getActionLabel(lt) {
    switch (lt) {
      case LT.Launch:
        return "应用启动";
      case LT.Hide:
        return "应用进入后台";
      case LT.Page:
        return "页面切换";
      case LT.Event:
        return "事件触发";
      case LT.Error:
        return "应用错误";
      case LT.Push:
        return "PUSH 设备标识";
      default:
        return `未知事件 (lt=${String(lt !== null && lt !== void 0 ? lt : "?")})`;
    }
  }
  function bucketSize(bucket) {
    let n2 = 0;
    for (const lt of Object.keys(bucket)) {
      const arr = bucket[lt];
      if (Array.isArray(arr))
        n2 += arr.length;
    }
    return n2;
  }
  function bucketSummary(bucket) {
    const parts = [];
    for (const lt of Object.keys(bucket)) {
      const arr = bucket[lt];
      if (Array.isArray(arr) && arr.length > 0) {
        parts.push(`lt=${lt}×${arr.length}`);
      }
    }
    return parts.join(", ") || "<空>";
  }
  function logCollect(data) {
    if (!logger.isDebug())
      return;
    const lt = data.lt;
    const label = getActionLabel(lt);
    logger.debug(`=== 统计数据采集：${label} (lt=${String(lt !== null && lt !== void 0 ? lt : "?")}) ===`);
    logger.debug(data);
    logger.debug("=== 采集结束 ===");
  }
  function logBoot(info) {
    if (!logger.isDebug())
      return;
    const timeoutParts = [];
    if (info.backgroundTimeoutSec != null) {
      timeoutParts.push(`后台超时(新会话): ${info.backgroundTimeoutSec}s`);
    }
    if (info.pageInactiveTimeoutSec != null) {
      timeoutParts.push(`前台无操作超时: ${info.pageInactiveTimeoutSec}s`);
    }
    const timeoutSeg = timeoutParts.length > 0 ? ` | ${timeoutParts.join(" | ")}` : "";
    const lines = [
      "=== uni统计 2.0 已启用 ===",
      `上报间隔: ${info.reportIntervalSec}s${timeoutSeg} | 应用APPID: ${info.ak || "<未注入>"}${info.appName ? ` | 应用名: ${info.appName}` : ""}${info.vueMode ? ` | ${info.vueMode}` : ""}`
    ];
    if (info.debugFromManifest) {
      lines.push("调试模式：已从 manifest.uniStatistics.debug 自动开启");
    }
    lines.push("=== 后续将在每次采集 / 上报时输出过程日志 ===");
    logger.debug(lines.join("\n"));
  }
  function logReportStart(info) {
    if (!logger.isDebug())
      return;
    const total = bucketSize(info.bucket);
    const summary = bucketSummary(info.bucket);
    logger.debug(`=== 准备上报：共 ${total} 条事件 (${summary}) ===`);
  }
  function logReportFailureReason(info) {
    if (!logger.isDebug())
      return;
    logger.debug(`原因: ${describeError(info.error)}`);
    if (info.persistedId) {
      logger.debug(`已暂存重试队列 [retryId=${info.persistedId}]，下次启动自动续传`);
    } else {
      logger.debug("未能写入重试队列：本批数据已丢弃");
    }
  }
  function logReportSummary(info) {
    if (!logger.isDebug())
      return;
    if (info.failedCount === 0) {
      logger.debug(`=== 上报成功： ${info.okCount} 条事件已送达, 用时 ${info.elapsedMs}ms ===`);
    } else if (info.okCount === 0) {
      logger.debug(`=== 上报失败： ${info.failedCount} 条事件未送达, 用时 ${info.elapsedMs}ms ===`);
    } else {
      logger.debug(`=== 上报完成：成功 ${info.okCount} 条，失败 ${info.failedCount} 条，用时 ${info.elapsedMs}ms ===`);
    }
  }
  function logNoChannel(info) {
    if (!logger.isDebug())
      return;
    logger.debug(`=== 上报跳过：当前无可用通道，已回滚 ${bucketSize(info.bucket)} 条事件入队 ===`);
  }
  function logRecoverStart(count) {
    if (!logger.isDebug())
      return;
    logger.debug(`=== 冷启续传：发现 ${count} 条历史 payload，开始逐条重发 ===`);
  }
  function logRecoverItem(info) {
    if (!logger.isDebug())
      return;
    if (info.ok) {
      logger.debug(`续传成功 (${info.index}/${info.total})`);
    } else {
      logger.debug(`续传失败 (${info.index}/${info.total})：${describeError(info.error)}`);
    }
  }
  function describeError(e) {
    if (!e)
      return "<无错误对象>";
    if (e instanceof Error) {
      return `${e.name}: ${e.message}`;
    }
    if (typeof e === "string")
      return e;
    return safeStringify(e) || String(e);
  }
  function omitEmptyStringFieldsForUpload(data) {
    const out = {};
    for (const key of Object.keys(data)) {
      const v = data[key];
      if (v === "")
        continue;
      out[key] = v;
    }
    return out;
  }
  const LT_ORDER = {
    "1": 1,
    "11": 2,
    "21": 3,
    "31": 4,
    "101": 5,
    "3": 100
  };
  const UNKNOWN_LT_WEIGHT = 50;
  function handleData(buckets) {
    return JSON.stringify(flatten(buckets));
  }
  function flatten(buckets) {
    const ltKeys = Object.keys(buckets);
    ltKeys.sort((a, b) => weightOf(a) - weightOf(b));
    const out = [];
    for (let i = 0; i < ltKeys.length; i++) {
      const lt = ltKeys[i];
      const list = buckets[lt];
      if (!list || list.length === 0)
        continue;
      for (let j = 0; j < list.length; j++)
        out.push(list[j]);
    }
    return out;
  }
  function weightOf(lt) {
    const w = LT_ORDER[lt];
    return typeof w === "number" ? w : UNKNOWN_LT_WEIGHT;
  }
  function chunkEvents(events, opts = {}) {
    var _a, _b;
    const maxEvents2 = (_a = opts.maxEvents) !== null && _a !== void 0 ? _a : Infinity;
    const maxBytes = (_b = opts.maxBytes) !== null && _b !== void 0 ? _b : Infinity;
    const out = [];
    if (!Array.isArray(events) || events.length === 0)
      return out;
    const safeMaxEvents = maxEvents2 > 0 ? maxEvents2 : Infinity;
    const safeMaxBytes = maxBytes > 0 ? maxBytes : Infinity;
    let cur = [];
    let curBytes = 2;
    for (let i = 0; i < events.length; i++) {
      const e = events[i];
      let s2 = "";
      try {
        s2 = JSON.stringify(e);
      } catch (_c) {
        continue;
      }
      const inc = cur.length === 0 ? s2.length : s2.length + 1;
      const wouldExceed = cur.length >= safeMaxEvents || cur.length > 0 && curBytes + inc > safeMaxBytes;
      if (wouldExceed) {
        out.push(cur);
        cur = [];
        curBytes = 2;
      }
      cur.push(e);
      curBytes += cur.length === 1 ? s2.length : s2.length + 1;
    }
    if (cur.length > 0)
      out.push(cur);
    return out;
  }
  function handleDataChunked(buckets, opts = {}) {
    const events = flatten(buckets);
    if (events.length === 0)
      return [];
    const chunks = chunkEvents(events, opts);
    const out = [];
    for (let i = 0; i < chunks.length; i++) {
      out.push(JSON.stringify(chunks[i]));
    }
    return out;
  }
  class PermanentChannelError extends Error {
    constructor(message) {
      super(message);
      this.permanent = true;
      this.name = "PermanentChannelError";
      Object.setPrototypeOf(this, PermanentChannelError.prototype);
    }
  }
  function isPermanentChannelError(err) {
    if (!err || typeof err !== "object")
      return false;
    if (err instanceof PermanentChannelError)
      return true;
    const e = err;
    if (e.name === "PermanentChannelError")
      return true;
    if (e.permanent === true)
      return true;
    return false;
  }
  function defaultGenPayloadId(nowMs2) {
    return "p-" + nowMs2.toString(36) + "-" + Math.random().toString(36).slice(2, 6);
  }
  function createCollector(deps) {
    let firstFlushDone = false;
    let deferredFlushTimer = null;
    function cancelDeferredFlush() {
      if (deferredFlushTimer == null)
        return;
      clearTimeout(deferredFlushTimer);
      deferredFlushTimer = null;
    }
    function triggerAutoFlush() {
      var _a;
      const deferMs = Math.max(0, Math.floor((_a = deps.firstFlushDeferMs) !== null && _a !== void 0 ? _a : 0));
      if (!firstFlushDone && deferMs > 0) {
        if (deferredFlushTimer != null)
          return;
        deferredFlushTimer = setTimeout(() => {
          deferredFlushTimer = null;
          firstFlushDone = true;
          void flushImpl(false).catch((e) => logger.warn("[uni统计 2.0] auto-flush failed", e));
        }, deferMs);
        return;
      }
      firstFlushDone = true;
      void flushImpl(false).catch((e) => logger.warn("[uni统计 2.0] auto-flush failed", e));
    }
    function report(input) {
      tryRun(() => {
        const t = typeof input.t === "number" ? input.t : deps.nowSec();
        const snap = deps.session.getSnapshot();
        let sessionForCtx;
        if (snap) {
          const seq = deps.session.nextSeq();
          sessionForCtx = Object.assign({}, snap, { seq });
        }
        if (snap && input.lt === LT.Event && deps.session.touch) {
          deps.session.touch(t);
        }
        const ctx = Object.assign({}, input, {
          t,
          session: sessionForCtx
        });
        const data = deps.builder.build(ctx);
        logCollect(data);
        deps.queue.enqueue(omitEmptyStringFieldsForUpload(data));
        if (deps.queue.shouldFlush()) {
          triggerAutoFlush();
        }
      }, void 0);
    }
    function applyUploadFields(bucket) {
      const fields = deps.resolveUploadFields ? deps.resolveUploadFields() : {};
      const keys = Object.keys(fields).filter((key) => {
        const v = fields[key];
        return v !== "" && v !== void 0 && v !== null;
      });
      if (keys.length === 0)
        return;
      for (const lt of Object.keys(bucket)) {
        const list = bucket[lt];
        if (!Array.isArray(list))
          continue;
        for (let i = 0; i < list.length; i++) {
          const item = list[i];
          for (let j = 0; j < keys.length; j++) {
            const key = keys[j];
            item[key] = fields[key];
          }
        }
      }
    }
    function applyUploadFieldsToRequests(requests) {
      const fields = deps.resolveUploadFields ? deps.resolveUploadFields() : {};
      const keys = Object.keys(fields).filter((key) => {
        const v = fields[key];
        return v !== "" && v !== void 0 && v !== null;
      });
      if (keys.length === 0)
        return requests;
      try {
        const events = JSON.parse(requests);
        if (!Array.isArray(events))
          return requests;
        for (let i = 0; i < events.length; i++) {
          const item = events[i];
          if (!item || typeof item !== "object")
            continue;
          for (let j = 0; j < keys.length; j++) {
            const key = keys[j];
            item[key] = fields[key];
          }
        }
        return JSON.stringify(events);
      } catch (_a) {
        return requests;
      }
    }
    function applyUploadFieldsToPayload(payload) {
      const requests = applyUploadFieldsToRequests(payload.requests);
      if (requests === payload.requests)
        return payload;
      return Object.assign({}, payload, { requests });
    }
    function flushImpl() {
      return __awaiter(this, arguments, void 0, function* (force = false) {
        var _a, _b, _c, _d, _e;
        if (!deps.queue.shouldFlush(force))
          return;
        if (deps.isNetworkOffline) {
          let offline = false;
          try {
            offline = yield deps.isNetworkOffline();
          } catch (_f) {
            offline = false;
          }
          if (offline) {
            logger.warn("[uni统计 2.0] 当前无网络，延后 flush");
            return;
          }
        }
        const snapshot = deps.queue.flush();
        if (!snapshot)
          return;
        applyUploadFields(snapshot);
        const channel = deps.selectChannel();
        if (!channel) {
          logger.warn("[uni统计 2.0] 无可用上报线路，本批已回滚队列");
          logNoChannel({ bucket: snapshot });
          deps.queue.rollback(snapshot);
          return;
        }
        const globalMaxBytes = (_b = (_a = deps.batchLimits) === null || _a === void 0 ? void 0 : _a.maxBytes) !== null && _b !== void 0 ? _b : BATCH_REQUESTS_MAX_BYTES;
        const channelMaxBytes = typeof channel.maxRequestBytes === "function" ? channel.maxRequestBytes() : Number.POSITIVE_INFINITY;
        const limits = {
          maxEvents: (_d = (_c = deps.batchLimits) === null || _c === void 0 ? void 0 : _c.maxEvents) !== null && _d !== void 0 ? _d : BATCH_MAX_EVENTS,
          maxBytes: Math.min(globalMaxBytes, channelMaxBytes)
        };
        const chunks = handleDataChunked(snapshot, limits);
        if (chunks.length === 0) {
          logger.warn("[uni统计 2.0] flush 切片结果为空，已回滚队列", snapshot);
          deps.queue.rollback(snapshot);
          return;
        }
        const startMs = deps.nowMs();
        let totalCount = 0;
        for (const lt of Object.keys(snapshot)) {
          const arr = snapshot[lt];
          if (Array.isArray(arr))
            totalCount += arr.length;
        }
        logReportStart({ channel: channel.name, bucket: snapshot });
        const hasLaunch = Array.isArray(snapshot["1"]) && snapshot["1"].length > 0;
        let okEvents = 0;
        let failedEvents = 0;
        let allOk = true;
        let firstChunkOk = true;
        for (let i = 0; i < chunks.length; i++) {
          const requests = chunks[i];
          const payload = {
            usv: deps.config.usv,
            t: deps.nowSec(),
            requests,
            _id: ((_e = deps.genPayloadId) !== null && _e !== void 0 ? _e : () => defaultGenPayloadId(deps.nowMs()))()
          };
          const sliceEvents = countEvents(requests);
          try {
            yield channel.send(payload);
            okEvents += sliceEvents;
          } catch (e) {
            allOk = false;
            if (i === 0)
              firstChunkOk = false;
            failedEvents += sliceEvents;
            if (isPermanentChannelError(e)) {
              logger.warn("[uni统计 2.0] 统计上报失败（本批已丢弃，不可重试）", e, "sliceBytes=" + requests.length);
              logReportFailureReason({ error: e, persistedId: void 0 });
              continue;
            }
            logger.warn("[uni统计 2.0] 统计上报失败（已暂存，下次启动自动重试）", e);
            const id = deps.retry.persist(payload);
            if (!id) {
              logger.warn("[uni统计 2.0] 统计暂存重试失败（无 retryId），本批已丢弃");
            }
            logReportFailureReason({ error: e, persistedId: id });
          }
        }
        const visitAccepted = hasLaunch ? firstChunkOk : allOk;
        if (visitAccepted) {
          tryRun(() => deps.visit.commitVisitOnAck(deps.nowSec()), void 0);
        } else {
          tryRun(() => deps.visit.rollbackPendingVisit(), void 0);
        }
        logReportSummary({
          channel: channel.name,
          okCount: okEvents,
          failedCount: failedEvents,
          elapsedMs: deps.nowMs() - startMs
        });
      });
    }
    function countEvents(requests) {
      try {
        const arr = JSON.parse(requests);
        return Array.isArray(arr) ? arr.length : 0;
      } catch (_a) {
        return 0;
      }
    }
    function recoverRetry() {
      return __awaiter(this, void 0, void 0, function* () {
        if (deps.isNetworkOffline) {
          let offline = false;
          try {
            offline = yield deps.isNetworkOffline();
          } catch (_a) {
            offline = false;
          }
          if (offline) {
            logger.warn("[uni统计 2.0] 当前无网络，延后续传重试");
            return;
          }
        }
        const items = deps.retry.loadAll();
        if (items.length === 0)
          return;
        const channel = deps.selectChannel();
        if (!channel) {
          logger.warn("[uni统计 2.0] 续传重试跳过：当前无可用上报线路");
          return;
        }
        logRecoverStart(items.length);
        let i = 0;
        for (const payload of items) {
          i++;
          const uploadPayload = applyUploadFieldsToPayload(payload);
          try {
            yield channel.send(uploadPayload);
            if (payload._id)
              deps.retry.ack(payload._id);
            logRecoverItem({
              index: i,
              total: items.length,
              payloadId: payload._id,
              ok: true
            });
          } catch (e) {
            if (isPermanentChannelError(e)) {
              if (payload._id)
                deps.retry.ack(payload._id);
              logger.warn("[uni统计 2.0] 续传重试失败（不可重试，已从队列移除）", e, "id=" + payload._id);
              logRecoverItem({
                index: i,
                total: items.length,
                payloadId: payload._id,
                ok: false,
                error: e
              });
              continue;
            }
            if (payload._id && deps.retry.markAttempt) {
              deps.retry.markAttempt(payload._id);
            }
            logger.warn("[uni统计 2.0] 续传重试失败（保留队列，下次启动再试）", e);
            logRecoverItem({
              index: i,
              total: items.length,
              payloadId: payload._id,
              ok: false,
              error: e
            });
          }
        }
      });
    }
    function flush2() {
      return __awaiter(this, arguments, void 0, function* (force = false) {
        cancelDeferredFlush();
        firstFlushDone = true;
        return flushImpl(force);
      });
    }
    function destroy() {
      cancelDeferredFlush();
      firstFlushDone = true;
    }
    return { report, flush: flush2, recoverRetry, destroy };
  }
  function getUni$6() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  function toQuery(payload) {
    const out = [];
    out.push("usv=" + encodeURIComponent(String(payload.usv)));
    out.push("t=" + encodeURIComponent(String(payload.t)));
    out.push("requests=" + encodeURIComponent(payload.requests));
    return out.join("&");
  }
  function tryImageRequest(payload, h5Url = STAT_H5_URL) {
    const ImageCtor = getGlobalObject().Image;
    if (typeof ImageCtor !== "function")
      return false;
    return tryRun(() => {
      const img = new ImageCtor();
      img.src = h5Url + "?" + toQuery(payload);
      return true;
    }, false);
  }
  function createHttpChannel(opts = {}) {
    var _a, _b, _c, _d, _e;
    const url = (_a = opts.url) !== null && _a !== void 0 ? _a : STAT_URL;
    const h5Url = (_b = opts.h5Url) !== null && _b !== void 0 ? _b : STAT_H5_URL;
    const ut = (_c = opts.ut) !== null && _c !== void 0 ? _c : "";
    const timeoutMs = (_d = opts.timeoutMs) !== null && _d !== void 0 ? _d : 1e4;
    const maxRetries = (_e = opts.maxRetries) !== null && _e !== void 0 ? _e : HTTP_MAX_RETRIES;
    function once(payload) {
      if (ut === "h5" && opts.preferImageOnH5 !== false) {
        if (tryImageRequest(payload, h5Url))
          return Promise.resolve();
      }
      const u = getUni$6();
      if (!u || typeof u.request !== "function") {
        return Promise.reject(new Error("uni.request unavailable"));
      }
      return new Promise((resolve, reject) => {
        let settled = false;
        const timer = setTimeout(() => {
          if (settled)
            return;
          settled = true;
          reject(new Error("http timeout"));
        }, timeoutMs);
        u.request({
          url,
          method: "POST",
          data: payload,
          timeout: timeoutMs,
          success: (res) => {
            var _a2;
            if (settled)
              return;
            settled = true;
            clearTimeout(timer);
            const code = (_a2 = res === null || res === void 0 ? void 0 : res.statusCode) !== null && _a2 !== void 0 ? _a2 : 0;
            if (code >= 200 && code < 300)
              resolve();
            else
              reject(new Error("http status " + code));
          },
          fail: (e) => {
            if (settled)
              return;
            settled = true;
            clearTimeout(timer);
            reject(e instanceof Error ? e : new Error(String(e)));
          }
        });
      });
    }
    return {
      name: "1.0",
      available() {
        const u = getUni$6();
        return !!(u && typeof u.request === "function");
      },
      send(payload) {
        return __awaiter(this, void 0, void 0, function* () {
          try {
            yield withRetry(() => once(payload), {
              times: maxRetries,
              baseDelayMs: RETRY_BASE_DELAY_MS,
              sleep: opts.sleep
            });
          } catch (e) {
            logger.warn("[uni统计 2.0] 统计上报失败（HTTP 已重试）", e);
            throw e;
          }
        });
      }
    };
  }
  const WEBTRACK_API_PATH = "/WebTrack";
  const WEBTRACK_BEACON_PATH = "/WebTrack.gif";
  function getUni$5() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  const REPORT_URL_BASE_OVERHEAD = 256;
  const REPORT_ENCODE_RATIO = 3;
  function buildStatReportUrl(payload, opts) {
    var _a;
    const t = ((_a = opts.nowMs) !== null && _a !== void 0 ? _a : () => Date.now())();
    const logs = encodeURIComponent(payload.requests);
    const host = opts.host.replace(/\/+$/, "");
    return host + opts.path + "?ProjectId=" + encodeURIComponent(opts.projectId) + "&TopicId=" + encodeURIComponent(opts.topicId) + "&Logs=" + logs + "&Source=webImg&Time=" + t;
  }
  function summarizeHttpErrorBody(data, maxLen = 320) {
    if (data == null)
      return "";
    if (typeof data === "string") {
      return data.length <= maxLen ? data : data.slice(0, maxLen) + "…";
    }
    try {
      const s2 = JSON.stringify(data);
      return s2.length <= maxLen ? s2 : s2.slice(0, maxLen) + "…";
    } catch (_a) {
      return String(data).slice(0, maxLen);
    }
  }
  function imageBeaconAwait(url, ms) {
    const ImageCtor = getGlobalObject().Image;
    if (typeof ImageCtor !== "function") {
      return Promise.reject(new PermanentChannelError("当前环境无法完成统计上报"));
    }
    return new Promise((resolve, reject) => {
      let settled = false;
      const timer = setTimeout(() => {
        if (settled)
          return;
        settled = true;
        reject(new Error("统计上报超时"));
      }, ms);
      const img = new ImageCtor();
      img.onload = () => {
        if (settled)
          return;
        settled = true;
        clearTimeout(timer);
        resolve();
      };
      img.onerror = () => {
        if (settled)
          return;
        settled = true;
        clearTimeout(timer);
        resolve();
      };
      img.src = url;
    });
  }
  function fetchBeaconAwait(url, ms) {
    const g = getGlobalObject();
    const fetchFn = g.fetch;
    if (typeof fetchFn !== "function") {
      return Promise.reject(new Error("fetch unavailable"));
    }
    const controller = typeof g.AbortController === "function" ? new g.AbortController() : void 0;
    return new Promise((resolve, reject) => {
      let settled = false;
      const timer = setTimeout(() => {
        if (settled)
          return;
        settled = true;
        if (controller)
          tryRun(() => controller.abort(), void 0);
        reject(new Error("统计上报超时"));
      }, ms);
      fetchFn(url, {
        method: "GET",
        keepalive: true,
        credentials: "omit",
        signal: controller ? controller.signal : void 0
      }).then((res) => {
        if (settled)
          return;
        settled = true;
        clearTimeout(timer);
        if (res && res.ok) {
          resolve();
          return;
        }
        reject(new Error("统计上报 HTTP " + (res ? res.status : 0)));
      }, (e) => {
        if (settled)
          return;
        settled = true;
        clearTimeout(timer);
        reject(e instanceof Error ? e : new Error(String(e)));
      });
    });
  }
  function getWxPreloadAssets() {
    const wx = getGlobalObject().wx;
    return typeof (wx === null || wx === void 0 ? void 0 : wx.preloadAssets) === "function" ? wx.preloadAssets : void 0;
  }
  function formatWxPreloadFail(err) {
    if (err instanceof Error)
      return err;
    if (err != null && typeof err === "object" && "errMsg" in err) {
      const msg = err.errMsg;
      if (typeof msg === "string" && msg.length > 0)
        return new Error(msg);
    }
    if (err == null)
      return new Error("preloadAssets fail (empty err)");
    return new Error(String(err));
  }
  function mpWeixinPreloadAssetsBeaconAwait(url, ms, preload) {
    return new Promise((resolve, reject) => {
      let settled = false;
      const timer = setTimeout(() => {
        if (settled)
          return;
        settled = true;
        reject(new Error("统计上报超时(preloadAssets)"));
      }, ms);
      try {
        preload({
          data: [{ type: "image", src: url }],
          success: () => {
            if (settled)
              return;
            settled = true;
            clearTimeout(timer);
            resolve();
          },
          fail: (err) => {
            if (settled)
              return;
            settled = true;
            clearTimeout(timer);
            reject(formatWxPreloadFail(err));
          }
        });
      } catch (e) {
        if (settled)
          return;
        settled = true;
        clearTimeout(timer);
        reject(e instanceof Error ? e : new Error(String(e)));
      }
    });
  }
  function isMpWeixinPreloadEnabled(opts) {
    var _a, _b;
    const enabled = (_a = opts.mpWeixinPreloadReport) !== null && _a !== void 0 ? _a : MP_WEIXIN_USE_PRELOAD_ASSETS_REPORT;
    if (!enabled)
      return false;
    const raw = (_b = opts.rawPlatform) !== null && _b !== void 0 ? _b : getRawPlatform();
    return raw === "mp-weixin";
  }
  function createImageChannel(opts = {}) {
    var _a, _b, _c, _d, _e, _f, _g;
    const host = (_a = opts.host) !== null && _a !== void 0 ? _a : IMAGE_REPORT_DEFAULTS.host;
    const projectId = (_b = opts.projectId) !== null && _b !== void 0 ? _b : IMAGE_REPORT_DEFAULTS.projectId;
    const topicId = (_c = opts.topicId) !== null && _c !== void 0 ? _c : IMAGE_REPORT_DEFAULTS.topicId;
    const timeoutMs = (_d = opts.timeoutMs) !== null && _d !== void 0 ? _d : 1e4;
    const maxRetries = (_e = opts.maxRetries) !== null && _e !== void 0 ? _e : IMAGE_MAX_RETRIES;
    const maxUrlLength = (_f = opts.maxUrlLength) !== null && _f !== void 0 ? _f : 6 * 1024;
    const preferBeacon = opts.preferImageBeacon !== false;
    const nowMs2 = opts.nowMs;
    const ut = (_g = opts.ut) !== null && _g !== void 0 ? _g : "";
    const isH52 = ut === "h5";
    const mpWeixinPreload = isMpWeixinPreloadEnabled(opts);
    function configured() {
      return !!(host && projectId && topicId);
    }
    const reportOpts = { host, projectId, topicId, nowMs: nowMs2 };
    function preflightUrl(payload, path) {
      if (!configured()) {
        throw new PermanentChannelError("统计上报未配置：请设置 TLS host、projectId、topicId");
      }
      const url = buildStatReportUrl(payload, {
        host: reportOpts.host,
        projectId: reportOpts.projectId,
        topicId: reportOpts.topicId,
        nowMs: reportOpts.nowMs,
        path
      });
      if (url.length > maxUrlLength) {
        throw new PermanentChannelError("统计上报 URL 过长: " + url.length + " > " + maxUrlLength);
      }
      return url;
    }
    function webTrackGetViaRequest(url) {
      const u = getUni$5();
      if (!u || typeof u.request !== "function") {
        return Promise.reject(new PermanentChannelError("当前环境无法完成统计上报"));
      }
      return new Promise((resolve, reject) => {
        let settled = false;
        const timer = setTimeout(() => {
          if (settled)
            return;
          settled = true;
          reject(new Error("统计上报超时"));
        }, timeoutMs);
        u.request({
          url,
          method: "GET",
          timeout: timeoutMs,
          success: (res) => {
            var _a2;
            if (settled)
              return;
            settled = true;
            clearTimeout(timer);
            const code = (_a2 = res === null || res === void 0 ? void 0 : res.statusCode) !== null && _a2 !== void 0 ? _a2 : 0;
            if (code >= 200 && code < 300) {
              resolve();
              return;
            }
            const hint = summarizeHttpErrorBody(res === null || res === void 0 ? void 0 : res.data);
            reject(new Error(hint ? `统计上报 HTTP ${code}: ${hint}` : `统计上报 HTTP ${code}`));
          },
          fail: (e) => {
            if (settled)
              return;
            settled = true;
            clearTimeout(timer);
            reject(e instanceof Error ? e : new Error(String(e)));
          }
        });
      });
    }
    function onceH5(payload) {
      const g = getGlobalObject();
      const u = getUni$5();
      const hasRequest = !!(u && typeof u.request === "function");
      if (preferBeacon && typeof g.fetch === "function") {
        return fetchBeaconAwait(preflightUrl(payload, WEBTRACK_BEACON_PATH), timeoutMs);
      }
      if (hasRequest) {
        return webTrackGetViaRequest(preflightUrl(payload, WEBTRACK_API_PATH));
      }
      if (preferBeacon && typeof g.Image === "function") {
        return imageBeaconAwait(preflightUrl(payload, WEBTRACK_BEACON_PATH), timeoutMs);
      }
      return Promise.reject(new PermanentChannelError("当前环境无法完成统计上报"));
    }
    function onceMpWeixin(payload) {
      const preloadFn = getWxPreloadAssets();
      if (preloadFn) {
        return mpWeixinPreloadAssetsBeaconAwait(preflightUrl(payload, WEBTRACK_BEACON_PATH), MP_WEIXIN_PRELOAD_TIMEOUT_MS, preloadFn);
      }
      logger.warn("[uni统计 2.0] wx.preloadAssets 不可用，回退 uni.request GET /WebTrack");
      return webTrackGetViaRequest(preflightUrl(payload, WEBTRACK_API_PATH));
    }
    function dispatchReport(payload) {
      if (isH52)
        return onceH5(payload);
      if (mpWeixinPreload)
        return onceMpWeixin(payload);
      return webTrackGetViaRequest(preflightUrl(payload, WEBTRACK_API_PATH));
    }
    return {
      name: "image",
      available() {
        return configured();
      },
      maxRequestBytes() {
        const raw = (maxUrlLength - REPORT_URL_BASE_OVERHEAD) / REPORT_ENCODE_RATIO;
        return Math.max(512, Math.floor(raw));
      },
      send(payload) {
        return __awaiter(this, void 0, void 0, function* () {
          try {
            yield withRetry(() => dispatchReport(payload), {
              times: maxRetries,
              baseDelayMs: RETRY_BASE_DELAY_MS,
              sleep: opts.sleep
            });
          } catch (e) {
            if (isPermanentChannelError(e)) {
              logger.warn("[uni统计 2.0] 统计上报失败（不可重试）", e);
            } else {
              logger.warn("[uni统计 2.0] 统计上报失败（已重试）", e);
            }
            throw e;
          }
        });
      }
    };
  }
  function s(v, def = "") {
    if (typeof v === "string")
      return v;
    if (typeof v === "number" && Number.isFinite(v))
      return String(v);
    return def;
  }
  function n(v, def = 0) {
    if (typeof v === "number" && Number.isFinite(v))
      return v;
    if (typeof v === "string" && v.length > 0) {
      const x = Number(v);
      if (Number.isFinite(x))
        return x;
    }
    return def;
  }
  function createStatDataBuilder(deps) {
    function baseFields() {
      var _a, _b, _c;
      const { config: config2, platform, system, locale, device, net, location, pkg, legacy, web } = deps;
      return {
        ak: s(config2.ak),
        usv: s(config2.usv),
        v: s((_a = config2.v) !== null && _a !== void 0 ? _a : system.appVersion),
        ch: s(config2.ch),
        ut: s(platform.ut),
        p: s((_b = platform.p) !== null && _b !== void 0 ? _b : system.osP),
        on: s(system.on),
        did: s(device.uuid),
        brand: s(system.brand),
        md: s(system.md),
        sv: s(system.sv),
        mpsdk: s(system.sdkVersion),
        mpv: s(system.mpvHostVersion),
        pr: n(locale.pr, 1),
        ww: n(locale.ww),
        wh: n(locale.wh),
        sw: n(locale.sw),
        sh: n(locale.sh),
        lang: s(locale.lang),
        net: s(net.net, "unknown"),
        lat: s(location.lat),
        lng: s(location.lng),
        mpn: s((_c = legacy === null || legacy === void 0 ? void 0 : legacy.mpn) !== null && _c !== void 0 ? _c : pkg.mpn),
        tdaid: s(pkg.tdaid),
        pkn: s(pkg.pkn),
        an: s(pkg.an),
        domain: s(web.domain)
      };
    }
    function sessionFields(ctx) {
      if (!ctx.session)
        return {};
      return {
        sid: ctx.session.sid,
        cst: ctx.session.sct
      };
    }
    function pageFields(ctx) {
      const out = {};
      if (ctx.url !== void 0)
        out.url = s(ctx.url);
      if (ctx.urlref !== void 0)
        out.urlref = s(ctx.urlref);
      if (ctx.urlref_ts !== void 0)
        out.urlref_ts = n(ctx.urlref_ts);
      if (ctx.ttn !== void 0)
        out.ttn = s(ctx.ttn);
      if (ctx.ttpj !== void 0)
        out.ttpj = s(ctx.ttpj);
      if (ctx.ttc !== void 0)
        out.ttc = s(ctx.ttc);
      return out;
    }
    function entryFields(ctx) {
      if (ctx.lt === "11") {
        return {
          iey: toIey(ctx.iey !== void 0 ? ctx.iey : false),
          ppiey: toIey(ctx.ppiey !== void 0 ? ctx.ppiey : false)
        };
      }
      return {};
    }
    function visitFields(ctx) {
      if (ctx.lt !== "1")
        return {};
      if (!ctx.visit)
        return {};
      return {
        fvts: ctx.visit.fvts,
        lvts: ctx.visit.lvts,
        tvc: ctx.visit.tvc
      };
    }
    function launchFields(ctx) {
      if (ctx.lt !== "1")
        return {};
      if (ctx.sc === void 0)
        return {};
      return { sc: s(ctx.sc) };
    }
    function errorFields(ctx) {
      if (ctx.lt !== "31" || !ctx.errMsg)
        return {};
      const ERR_MSG_MAX = 3 * 1024;
      const TRUNC_SUFFIX = "…[truncated]";
      let em = s(ctx.errMsg);
      if (em.length > ERR_MSG_MAX) {
        em = em.slice(0, ERR_MSG_MAX - TRUNC_SUFFIX.length) + TRUNC_SUFFIX;
      }
      return { em };
    }
    function pushFields(ctx) {
      if (ctx.lt !== "101" || !ctx.cid)
        return {};
      return { cid: s(ctx.cid) };
    }
    function build(ctx) {
      const safeCustom = {};
      if (ctx.custom) {
        const reserved = /* @__PURE__ */ new Set([
          "lt",
          "t",
          "sid",
          "cst",
          "did",
          "p",
          "on",
          "mpv",
          "domain",
          "fvts",
          "lvts",
          "tvc",
          "sc"
        ]);
        for (const k of Object.keys(ctx.custom)) {
          if (!reserved.has(k))
            safeCustom[k] = ctx.custom[k];
        }
      }
      const out = { lt: ctx.lt, t: n(ctx.t) };
      Object.assign(out, baseFields(), sessionFields(ctx), pageFields(ctx), entryFields(ctx), visitFields(ctx), launchFields(ctx), errorFields(ctx), pushFields(ctx), safeCustom);
      return out;
    }
    return { build };
  }
  function normalizeChannelValue(value) {
    if (typeof value === "string")
      return value;
    if (typeof value === "number" && Number.isFinite(value))
      return String(value);
    return "";
  }
  function getAppChannel() {
    const plus2 = getGlobalObject().plus;
    if (!isApp() && !(plus2 === null || plus2 === void 0 ? void 0 : plus2.runtime))
      return "";
    const raw = tryRun(() => {
      var _a;
      return (_a = plus2 === null || plus2 === void 0 ? void 0 : plus2.runtime) === null || _a === void 0 ? void 0 : _a.channel;
    }, void 0);
    return normalizeChannelValue(raw);
  }
  let cachedStatic = null;
  function getUni$4() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  function mergeWxHostSnapshots() {
    const raw = getRawPlatform();
    if (raw !== "mp-weixin" && raw !== "mp-qq")
      return null;
    const wxHost = getGlobalObject().wx;
    if (!wxHost)
      return null;
    const sync = typeof wxHost.getSystemInfoSync === "function" ? tryRun(() => wxHost.getSystemInfoSync(), null) : null;
    const device = typeof wxHost.getDeviceInfo === "function" ? tryRun(() => wxHost.getDeviceInfo(), null) : null;
    const appBase = typeof wxHost.getAppBaseInfo === "function" ? tryRun(() => wxHost.getAppBaseInfo(), null) : null;
    const windowInfo = typeof wxHost.getWindowInfo === "function" ? tryRun(() => wxHost.getWindowInfo(), null) : null;
    return mergeSystemSnapshots(sync, device, appBase, windowInfo);
  }
  function mergeSystemSnapshots(...parts) {
    const out = {};
    for (const p of parts) {
      if (!p)
        continue;
      for (const k of Object.keys(p)) {
        const v = p[k];
        if (v !== void 0 && v !== null)
          out[k] = v;
      }
    }
    return out;
  }
  function mergedSystemInfo() {
    const u = getUni$4();
    const sync = u && typeof u.getSystemInfoSync === "function" ? tryRun(() => u.getSystemInfoSync(), null) : null;
    const device = u && typeof u.getDeviceInfo === "function" ? tryRun(() => u.getDeviceInfo(), null) : null;
    const appBase = u && typeof u.getAppBaseInfo === "function" ? tryRun(() => u.getAppBaseInfo(), null) : null;
    const windowInfo = u && typeof u.getWindowInfo === "function" ? tryRun(() => u.getWindowInfo(), null) : null;
    const fromUni = mergeSystemSnapshots(sync, device, appBase, windowInfo);
    const fromWx = mergeWxHostSnapshots();
    const merged = fromWx ? mergeSystemSnapshots(fromUni, fromWx) : fromUni;
    return merged;
  }
  function resolveUniConfigAppVersion() {
    return tryRun(() => {
      const cfg = getGlobalObject().__uniConfig;
      return typeof (cfg === null || cfg === void 0 ? void 0 : cfg.appVersion) === "string" ? cfg.appVersion : "";
    }, "");
  }
  function resolveBuildTimeAppVersion() {
    const raw = "0.1.0";
    return typeof raw === "string" ? raw : "";
  }
  function resolveAppVersionForStat(plus2, sys) {
    var _a;
    const fromPlus = (_a = plus2 === null || plus2 === void 0 ? void 0 : plus2.runtime) === null || _a === void 0 ? void 0 : _a.version;
    if (typeof fromPlus === "string" && fromPlus)
      return fromPlus;
    const fromSys = sys.appVersion;
    if (typeof fromSys === "string" && fromSys)
      return fromSys;
    const fromUniConfig = resolveUniConfigAppVersion();
    if (fromUniConfig)
      return fromUniConfig;
    return resolveBuildTimeAppVersion();
  }
  function buildOnForStat(sys) {
    const rom = typeof sys.romName === "string" ? sys.romName.trim() : "";
    if (rom) {
      const romVer = typeof sys.romVersion === "string" ? sys.romVersion.trim() : "";
      return romVer ? `${rom} ${romVer}`.trim() : rom;
    }
    return typeof sys.osName === "string" ? sys.osName.trim() : "";
  }
  function getSystemInfo() {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o, _p, _q, _r, _s, _t;
    if (cachedStatic)
      return cachedStatic;
    const sys = mergedSystemInfo();
    const plus2 = getGlobalObject().plus;
    const appVersion = resolveAppVersionForStat(plus2, sys);
    cachedStatic = {
      brand: (_b = (_a = sys.deviceBrand) !== null && _a !== void 0 ? _a : sys.brand) !== null && _b !== void 0 ? _b : "",
      md: (_d = (_c = sys.deviceModel) !== null && _c !== void 0 ? _c : sys.model) !== null && _d !== void 0 ? _d : "",
      sv: (_f = (_e = sys.osVersion) !== null && _e !== void 0 ? _e : sys.system) !== null && _f !== void 0 ? _f : "",
      v: (_h = (_g = sys.hostVersion) !== null && _g !== void 0 ? _g : sys.version) !== null && _h !== void 0 ? _h : "",
      ut: (_j = sys.deviceType) !== null && _j !== void 0 ? _j : "unknown",
      appVersion,
      appWgtVersion: (_p = (_o = (_l = (_k = plus2 === null || plus2 === void 0 ? void 0 : plus2.runtime) === null || _k === void 0 ? void 0 : _k.appWgtVersion) !== null && _l !== void 0 ? _l : (_m = plus2 === null || plus2 === void 0 ? void 0 : plus2.runtime) === null || _m === void 0 ? void 0 : _m.appWgtRevision) !== null && _o !== void 0 ? _o : sys.appWgtVersion) !== null && _p !== void 0 ? _p : "",
      mpvHostVersion: ((_r = (_q = sys.hostVersion) !== null && _q !== void 0 ? _q : sys.version) !== null && _r !== void 0 ? _r : "").trim(),
      on: buildOnForStat(sys),
      sdkVersion: (_t = (_s = sys.hostSDKVersion) !== null && _s !== void 0 ? _s : sys.SDKVersion) !== null && _t !== void 0 ? _t : "",
      statusBarHeight: typeof sys.statusBarHeight === "number" ? sys.statusBarHeight : 0,
      osP: normalizeStatOsP({
        platform: sys.platform,
        osName: sys.osName,
        system: sys.system
      })
    };
    return cachedStatic;
  }
  function getLocaleAndScreen() {
    var _a, _b;
    const sys = mergedSystemInfo();
    const prRaw = typeof sys.pixelRatio === "number" ? sys.pixelRatio : typeof sys.devicePixelRatio === "number" ? sys.devicePixelRatio : 1;
    return {
      lang: ((_b = (_a = sys.hostLanguage) !== null && _a !== void 0 ? _a : sys.language) !== null && _b !== void 0 ? _b : "").replace(/_/g, "-"),
      ww: typeof sys.windowWidth === "number" ? sys.windowWidth : 0,
      wh: typeof sys.windowHeight === "number" ? sys.windowHeight : 0,
      sw: typeof sys.screenWidth === "number" ? sys.screenWidth : 0,
      sh: typeof sys.screenHeight === "number" ? sys.screenHeight : 0,
      pr: prRaw > 0 ? prRaw : 1
    };
  }
  let cached$1 = null;
  function getUni$3() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  function getPlus() {
    return getGlobalObject().plus;
  }
  function getMpTdaid(platform) {
    const u = getUni$3();
    switch (platform) {
      case "wx":
      case "qq": {
        if (typeof (u === null || u === void 0 ? void 0 : u.getAccountInfoSync) === "function") {
          const id = tryRun(() => {
            var _a, _b;
            return (_b = (_a = u.getAccountInfoSync().miniProgram) === null || _a === void 0 ? void 0 : _a.appId) !== null && _b !== void 0 ? _b : "";
          }, "");
          if (id)
            return id;
        }
        const wxHost = getGlobalObject().wx;
        if (typeof (wxHost === null || wxHost === void 0 ? void 0 : wxHost.getAccountInfoSync) === "function") {
          const id2 = tryRun(() => {
            var _a, _b;
            return (_b = (_a = wxHost.getAccountInfoSync().miniProgram) === null || _a === void 0 ? void 0 : _a.appId) !== null && _b !== void 0 ? _b : "";
          }, "");
          if (id2)
            return id2;
        }
        const envId = "";
        return typeof envId === "string" ? envId : "";
      }
      case "ali":
      case "dt": {
        const my = getGlobalObject().my;
        if (!my)
          return "";
        const v1 = tryRun(() => {
          var _a, _b;
          return (_b = (_a = my.getAppIdSync) === null || _a === void 0 ? void 0 : _a.call(my)) !== null && _b !== void 0 ? _b : "";
        }, "");
        if (v1)
          return v1;
        return tryRun(() => {
          var _a, _b, _c;
          return (_c = (_b = (_a = my.getAccountInfoSync) === null || _a === void 0 ? void 0 : _a.call(my).miniProgram) === null || _b === void 0 ? void 0 : _b.appId) !== null && _c !== void 0 ? _c : "";
        }, "");
      }
      case "tt":
      case "lark": {
        const tt = getGlobalObject().tt;
        return tryRun(() => {
          var _a, _b, _c;
          return (_c = (_b = (_a = tt === null || tt === void 0 ? void 0 : tt.getEnvInfoSync) === null || _a === void 0 ? void 0 : _a.call(tt).microapp) === null || _b === void 0 ? void 0 : _b.appId) !== null && _c !== void 0 ? _c : "";
        }, "");
      }
      case "bd": {
        const swan = getGlobalObject().swan;
        return tryRun(() => {
          var _a, _b, _c;
          return (_c = (_b = (_a = swan === null || swan === void 0 ? void 0 : swan.getEnvInfoSync) === null || _a === void 0 ? void 0 : _a.call(swan).common) === null || _b === void 0 ? void 0 : _b.appKey) !== null && _c !== void 0 ? _c : "";
        }, "");
      }
      default:
        return "";
    }
  }
  function getAppPkn() {
    var _a, _b, _c;
    const plus2 = getPlus();
    if (!plus2)
      return "";
    const osName = (_c = (_b = (_a = plus2.os) === null || _a === void 0 ? void 0 : _a.name) === null || _b === void 0 ? void 0 : _b.toLowerCase()) !== null && _c !== void 0 ? _c : "";
    if (osName.includes("android")) {
      return tryRun(() => {
        var _a2, _b2, _c2, _d, _e;
        return (_e = (_d = (_c2 = (_b2 = (_a2 = plus2.android) === null || _a2 === void 0 ? void 0 : _a2.runtimeMainActivity) === null || _b2 === void 0 ? void 0 : _b2.call(_a2)) === null || _c2 === void 0 ? void 0 : _c2.getPackageName) === null || _d === void 0 ? void 0 : _d.call(_c2)) !== null && _e !== void 0 ? _e : "";
      }, "");
    }
    if (osName === "ios" || osName === "iphone os") {
      const v = tryRun(() => {
        var _a2, _b2;
        return (_b2 = (_a2 = plus2.ios) === null || _a2 === void 0 ? void 0 : _a2.bundleId) !== null && _b2 !== void 0 ? _b2 : "";
      }, "");
      return v || tryRun(() => {
        var _a2, _b2;
        return (_b2 = (_a2 = plus2.runtime) === null || _a2 === void 0 ? void 0 : _a2.appid) !== null && _b2 !== void 0 ? _b2 : "";
      }, "");
    }
    return tryRun(() => {
      var _a2, _b2;
      return (_b2 = (_a2 = plus2.runtime) === null || _a2 === void 0 ? void 0 : _a2.appid) !== null && _b2 !== void 0 ? _b2 : "";
    }, "");
  }
  function getAppName() {
    const plus2 = getPlus();
    if (!plus2)
      return "";
    return tryRun(() => {
      var _a, _b;
      return (_b = (_a = plus2.runtime) === null || _a === void 0 ? void 0 : _a.appname) !== null && _b !== void 0 ? _b : "";
    }, "") || tryRun(() => {
      var _a, _b;
      return (_b = (_a = plus2.runtime) === null || _a === void 0 ? void 0 : _a.name) !== null && _b !== void 0 ? _b : "";
    }, "");
  }
  function getEnvAppName() {
    var _a;
    return (_a = "鹊桥 · 红娘工作台") !== null && _a !== void 0 ? _a : "";
  }
  function getH5AppName() {
    const env = getEnvAppName();
    if (env)
      return env;
    return tryRun(() => {
      var _a, _b;
      return (_b = (_a = getGlobalObject().document) === null || _a === void 0 ? void 0 : _a.title) !== null && _b !== void 0 ? _b : "";
    }, "");
  }
  function getPackageInfo() {
    if (cached$1)
      return cached$1;
    const platform = getPlatform();
    let mpn = "";
    let tdaid = "";
    let pkn = "";
    let an = "";
    if (isApp()) {
      tdaid = tryRun(() => {
        var _a, _b, _c;
        return (_c = (_b = (_a = getPlus()) === null || _a === void 0 ? void 0 : _a.runtime) === null || _b === void 0 ? void 0 : _b.appid) !== null && _c !== void 0 ? _c : "";
      }, "");
      pkn = getAppPkn() || tdaid;
      an = getAppName() || getEnvAppName();
      mpn = pkn || tdaid;
    } else if (isMp()) {
      tdaid = getMpTdaid(platform);
      pkn = "";
      an = getEnvAppName();
      mpn = tdaid || "";
    } else if (isH5()) {
      tdaid = "";
      pkn = "";
      an = getH5AppName();
      mpn = "";
    } else {
      tdaid = "";
      pkn = "";
      an = getEnvAppName();
      mpn = "";
    }
    cached$1 = { mpn, tdaid, pkn, an };
    return cached$1;
  }
  const EMPTY_WEB_INFO = { domain: "" };
  let cached = null;
  function readWebDomainFromLocation(loc) {
    const protocol = typeof loc.protocol === "string" ? loc.protocol.toLowerCase() : "";
    if (protocol !== "http:" && protocol !== "https:")
      return "";
    if (typeof loc.origin === "string" && loc.origin.trim()) {
      return loc.origin.trim();
    }
    const host = typeof loc.host === "string" && loc.host.trim() ? loc.host.trim() : typeof loc.hostname === "string" ? loc.hostname.trim() : "";
    if (!host)
      return "";
    return `${protocol}//${host}`;
  }
  function getWebInfo() {
    if (!isH5())
      return EMPTY_WEB_INFO;
    if (cached !== null)
      return cached;
    cached = tryRun(() => {
      const win = getGlobalObject();
      const loc = win.location;
      if (!loc)
        return EMPTY_WEB_INFO;
      return { domain: readWebDomainFromLocation(loc) };
    }, EMPTY_WEB_INFO);
    return cached;
  }
  const registry = /* @__PURE__ */ new Map();
  const installedFanout = /* @__PURE__ */ new Map();
  function add(api2, handlers) {
    var _a;
    const set2 = (_a = registry.get(api2)) !== null && _a !== void 0 ? _a : /* @__PURE__ */ new Set();
    set2.add(handlers);
    registry.set(api2, set2);
    reinstall(api2);
    return () => {
      const cur = registry.get(api2);
      if (!cur)
        return;
      cur.delete(handlers);
      if (cur.size === 0) {
        registry.delete(api2);
        const prev = installedFanout.get(api2);
        installedFanout.delete(api2);
        if (prev) {
          try {
            getUni$2().removeInterceptor(api2, prev);
          } catch (_a2) {
          }
        }
      } else {
        reinstall(api2);
      }
    };
  }
  function buildFanout(set2) {
    return {
      invoke(args) {
        let blocked = false;
        for (const h of set2) {
          if (!h.invoke)
            continue;
          const r = h.invoke(args);
          if (r === false)
            blocked = true;
        }
        return blocked ? false : void 0;
      },
      success(res) {
        var _a;
        for (const h of set2)
          (_a = h.success) === null || _a === void 0 ? void 0 : _a.call(h, res);
      },
      fail(err) {
        var _a;
        for (const h of set2)
          (_a = h.fail) === null || _a === void 0 ? void 0 : _a.call(h, err);
      },
      complete(res) {
        var _a;
        for (const h of set2)
          (_a = h.complete) === null || _a === void 0 ? void 0 : _a.call(h, res);
      },
      returnValue(res) {
        let v = res;
        for (const h of set2) {
          if (!h.returnValue)
            continue;
          v = h.returnValue(v);
        }
        return v;
      }
    };
  }
  function reinstall(api2) {
    const set2 = registry.get(api2);
    if (!set2 || set2.size === 0)
      return;
    const fanout = buildFanout(set2);
    try {
      const uni2 = getUni$2();
      const prev = installedFanout.get(api2);
      if (prev) {
        try {
          uni2.removeInterceptor(api2, prev);
        } catch (_a) {
        }
      }
      uni2.addInterceptor(api2, fanout);
      installedFanout.set(api2, fanout);
    } catch (_b) {
    }
  }
  function getUni$2() {
    const raw = resolveUniRuntime();
    const u = raw != null && typeof raw === "object" ? raw : void 0;
    if (!u)
      throw new Error("[uni统计 2.0] uni interceptor API is not available");
    return u;
  }
  function __reset() {
    registry.clear();
    installedFanout.clear();
  }
  const interceptor = { add, __reset };
  function registerLoginInterceptor(reporter) {
    return interceptor.add("login", {
      complete() {
        reporter.report({ lt: LT.Event, custom: { e_n: "login" } });
      }
    });
  }
  function registerNavigationBarInterceptor() {
    return interceptor.add("setNavigationBarTitle", {
      invoke(args) {
        const a = args;
        if (a && "title" in a)
          setPageTitle(a.title);
      }
    });
  }
  function registerPaymentInterceptor(reporter) {
    return interceptor.add("requestPayment", {
      success() {
        reporter.report({ lt: LT.Event, custom: { e_n: "pay_success" } });
      },
      fail() {
        reporter.report({ lt: LT.Event, custom: { e_n: "pay_fail" } });
      }
    });
  }
  function registerShareInterceptor(reporter) {
    const fire = () => reporter.report({ lt: LT.Event, custom: { e_n: "share" } });
    return interceptor.add("share", {
      success() {
        fire();
      },
      fail() {
        fire();
      }
    });
  }
  function installAllInterceptors(reporter) {
    const unbinders = [
      registerLoginInterceptor(reporter),
      registerShareInterceptor(reporter),
      registerPaymentInterceptor(reporter),
      registerNavigationBarInterceptor()
    ];
    return () => {
      for (const u of unbinders) {
        try {
          u();
        } catch (_a) {
        }
      }
    };
  }
  const KEY_DONE = "migration:done";
  const KEY_MAP = [
    ["__first__visit__time", "visit:fvts"],
    ["__last__visit__time", "visit:lvts"],
    ["__total__visit__count", "visit:tvc"]
  ];
  function getAppId() {
    const id = "";
    if (id.length > 0)
      return id;
    return "default";
  }
  function readLegacyAggregate() {
    const u = resolveUniRuntime();
    if (!u || typeof u.getStorageSync !== "function")
      return null;
    const key = `${LEGACY_NAMESPACE_ROOT}:${getAppId()}`;
    const raw = tryRun(() => u.getStorageSync(key), null);
    if (raw && typeof raw === "object")
      return raw;
    return null;
  }
  let ran = false;
  function migrateLegacyData() {
    if (ran)
      return false;
    ran = true;
    const doneR = storage.safeRead(KEY_DONE);
    if (doneR.ok && doneR.value)
      return false;
    const legacy = readLegacyAggregate();
    if (!legacy) {
      storage.set(KEY_DONE, 1);
      return false;
    }
    let migrated = 0;
    for (let i = 0; i < KEY_MAP.length; i++) {
      const [oldKey, newKey] = KEY_MAP[i];
      if (!(oldKey in legacy))
        continue;
      const value = legacy[oldKey];
      const existing = storage.safeRead(newKey);
      if (existing.ok && existing.value !== void 0)
        continue;
      storage.set(newKey, value);
      migrated++;
    }
    storage.set(KEY_DONE, 1);
    if (migrated > 0) {
      logger.info("[uni统计 2.0] migrated legacy keys", migrated);
    }
    return migrated > 0;
  }
  function selectChannel(opts) {
    var _a;
    const version = (_a = opts.version) !== null && _a !== void 0 ? _a : "image";
    const fallback = opts.fallbackToHttp !== false;
    if (version === "1") {
      if (opts.http && opts.http.available())
        return opts.http;
      return void 0;
    }
    if (version === "2") {
      if (opts.cloud && opts.cloud.available())
        return opts.cloud;
      if (!fallback) {
        logger.warn("[uni统计 2.0] 云函数上报不可用且已关闭 HTTP 兜底，本批已丢弃");
        return void 0;
      }
      if (opts.http && opts.http.available()) {
        logger.warn("[uni统计 2.0] 云函数上报不可用，已降级为 HTTP 上报");
        return opts.http;
      }
      logger.warn("[uni统计 2.0] 无可用上报线路");
      return void 0;
    }
    if (opts.image && opts.image.available())
      return opts.image;
    if (!fallback) {
      if (opts.image) {
        logger.warn("[uni统计 2.0] 统计上报线路不可用且已关闭 HTTP 兜底，本批已丢弃");
      }
      return void 0;
    }
    if (opts.http && opts.http.available()) {
      if (opts.image) {
        logger.warn("[uni统计 2.0] 统计上报线路不可用，已降级为 HTTP 上报");
      }
      return opts.http;
    }
    logger.warn("[uni统计 2.0] 无可用上报线路");
    return void 0;
  }
  const DEFAULT_RESULT = { net: "unknown", raw: "" };
  const NET_MAP = {
    wifi: "wifi",
    "2g": "2g",
    "3g": "3g",
    "4g": "4g",
    "5g": "5g",
    ethernet: "ethernet",
    none: "none",
    unknown: "unknown"
  };
  function getUni$1() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  function normalizeNet(raw) {
    var _a;
    if (typeof raw !== "string" || raw.length === 0)
      return "unknown";
    return (_a = NET_MAP[raw.toLowerCase()]) !== null && _a !== void 0 ? _a : "unknown";
  }
  function getNet(timeoutMs = 1500) {
    return new Promise((resolve) => {
      const u = getUni$1();
      if (!u || typeof u.getNetworkType !== "function") {
        resolve(DEFAULT_RESULT);
        return;
      }
      let settled = false;
      const finish = (r) => {
        if (settled)
          return;
        settled = true;
        resolve(r);
      };
      const timer = setTimeout(() => finish(DEFAULT_RESULT), timeoutMs);
      tryRun(() => u.getNetworkType({
        success: (res) => {
          var _a;
          clearTimeout(timer);
          const raw = (_a = res === null || res === void 0 ? void 0 : res.networkType) !== null && _a !== void 0 ? _a : "";
          finish({ net: normalizeNet(raw), raw });
        },
        fail: () => {
          clearTimeout(timer);
          finish(DEFAULT_RESULT);
        }
      }), void 0);
    });
  }
  function onChange(cb) {
    const u = getUni$1();
    if (!u || typeof u.onNetworkStatusChange !== "function") {
      return () => {
      };
    }
    const wrapped = (res) => {
      var _a;
      const raw = (_a = res === null || res === void 0 ? void 0 : res.networkType) !== null && _a !== void 0 ? _a : "";
      const net = (res === null || res === void 0 ? void 0 : res.isConnected) === false ? "none" : normalizeNet(raw);
      tryRun(() => cb({ net, raw }), void 0);
    };
    tryRun(() => u.onNetworkStatusChange(wrapped), void 0);
    return () => {
      if (typeof u.offNetworkStatusChange === "function") {
        tryRun(() => u.offNetworkStatusChange(wrapped), void 0);
      }
    };
  }
  function isOfflineNetResult(r) {
    return r.net === "none";
  }
  function isNetworkOffline() {
    return __awaiter(this, void 0, void 0, function* () {
      const r = yield getNet();
      return isOfflineNetResult(r);
    });
  }
  function onNetworkOnline(cb) {
    return onChange((r) => {
      if (isOfflineNetResult(r))
        return;
      cb();
    });
  }
  const STORAGE_KEY$1 = "queue";
  const DEFAULT_SINGLE_EVENT_MAX_BYTES = SINGLE_EVENT_MAX_BYTES;
  const state = {
    bucket: {},
    lastFlushAt: 0
  };
  let intervalSec = REPORT_INTERVAL_SEC;
  let singleEventMaxBytes = DEFAULT_SINGLE_EVENT_MAX_BYTES;
  let maxEvents = QUEUE_MAX_EVENTS;
  let restored = false;
  let capacityWarned = false;
  function configure(opts) {
    if (typeof opts.intervalSec === "number" && opts.intervalSec >= 0) {
      intervalSec = Math.floor(opts.intervalSec);
    }
    if (typeof opts.singleEventMaxBytes === "number" && opts.singleEventMaxBytes > 0) {
      singleEventMaxBytes = Math.floor(opts.singleEventMaxBytes);
    }
    if (typeof opts.maxEvents === "number" && opts.maxEvents > 0) {
      maxEvents = Math.floor(opts.maxEvents);
    }
  }
  function enforceCapacity() {
    let total = size();
    if (total <= maxEvents) {
      capacityWarned = false;
      return;
    }
    const dropped = total - maxEvents;
    while (total > maxEvents) {
      let largestLt = "";
      let largestLen = 0;
      for (const lt of Object.keys(state.bucket)) {
        const len = state.bucket[lt].length;
        if (len > largestLen) {
          largestLen = len;
          largestLt = lt;
        }
      }
      if (!largestLt || largestLen === 0)
        break;
      state.bucket[largestLt].shift();
      if (state.bucket[largestLt].length === 0)
        delete state.bucket[largestLt];
      total--;
    }
    if (!capacityWarned) {
      capacityWarned = true;
      logger.warn("[uni统计 2.0] 上报队列超过容量上限，已丢弃最旧事件", "dropped=" + dropped, "limit=" + maxEvents);
    }
  }
  function persistBucket() {
    if (Object.keys(state.bucket).length === 0) {
      storage.remove(STORAGE_KEY$1);
      return;
    }
    try {
      storage.set(STORAGE_KEY$1, state.bucket);
    } catch (e) {
      logger.warn("[uni统计 2.0] queue persist failed", e);
    }
  }
  function restoreOnce() {
    if (restored)
      return;
    restored = true;
    const raw = storage.safeRead(STORAGE_KEY$1);
    if (!raw.ok || !raw.value || typeof raw.value !== "object")
      return;
    const persisted = raw.value;
    for (const lt of Object.keys(persisted)) {
      const arr = persisted[lt];
      if (!Array.isArray(arr) || arr.length === 0)
        continue;
      if (!state.bucket[lt])
        state.bucket[lt] = [];
      state.bucket[lt].push(...arr);
    }
  }
  function enqueue(data) {
    var _a;
    if (!data || typeof data !== "object")
      return;
    const lt = String((_a = data.lt) !== null && _a !== void 0 ? _a : "");
    if (!lt) {
      logger.warn("[uni统计 2.0] enqueue dropped: missing lt", data);
      return;
    }
    let serialized = "";
    try {
      serialized = JSON.stringify(data);
    } catch (e) {
      logger.warn("[uni统计 2.0] enqueue dropped: stringify failed", e);
      return;
    }
    if (serialized.length > singleEventMaxBytes) {
      logger.warn("[uni统计 2.0] enqueue dropped: single event too large", "lt=" + lt, "bytes=" + serialized.length, "limit=" + singleEventMaxBytes);
      return;
    }
    restoreOnce();
    if (!state.bucket[lt])
      state.bucket[lt] = [];
    state.bucket[lt].push(data);
    enforceCapacity();
    persistBucket();
  }
  function shouldFlush(force = false) {
    if (force)
      return true;
    if (intervalSec <= 0)
      return true;
    const elapsedSec = (nowMs() - state.lastFlushAt) / 1e3;
    return elapsedSec >= intervalSec;
  }
  function flush() {
    restoreOnce();
    const lts = Object.keys(state.bucket);
    if (lts.length === 0)
      return void 0;
    const snapshot = state.bucket;
    state.bucket = {};
    state.lastFlushAt = nowMs();
    storage.remove(STORAGE_KEY$1);
    return snapshot;
  }
  function rollback(snapshot) {
    if (!snapshot)
      return;
    for (const lt of Object.keys(snapshot)) {
      const arr = snapshot[lt];
      if (!Array.isArray(arr) || arr.length === 0)
        continue;
      if (!state.bucket[lt])
        state.bucket[lt] = [];
      state.bucket[lt] = arr.concat(state.bucket[lt]);
    }
    enforceCapacity();
    persistBucket();
  }
  function size() {
    let n2 = 0;
    for (const lt of Object.keys(state.bucket)) {
      n2 += state.bucket[lt].length;
    }
    return n2;
  }
  const STORAGE_KEY = "retry:queue";
  const DEFAULT_MAX_ITEMS = 50;
  const DEFAULT_MAX_AGE_MS = 7 * 24 * 60 * 60 * 1e3;
  const DEFAULT_MAX_ATTEMPTS = RETRY_MAX_ATTEMPTS;
  const config = {
    maxItems: DEFAULT_MAX_ITEMS,
    maxAgeMs: DEFAULT_MAX_AGE_MS,
    maxAttempts: DEFAULT_MAX_ATTEMPTS
  };
  function readQueue() {
    const raw = storage.safeRead(STORAGE_KEY);
    if (!raw.ok || !Array.isArray(raw.value))
      return [];
    return raw.value.filter((it) => it && typeof it.id === "string" && it.payload && typeof it.payload === "object");
  }
  function writeQueue(items) {
    if (items.length === 0) {
      storage.remove(STORAGE_KEY);
      return;
    }
    storage.set(STORAGE_KEY, items);
  }
  function genId(payload) {
    if (payload._id)
      return payload._id;
    return "r-" + nowMs().toString(36) + "-" + Math.random().toString(36).slice(2, 6);
  }
  function persist(payload) {
    if (!payload)
      return void 0;
    const id = genId(payload);
    const items = readQueue();
    if (items.some((it) => it.id === id)) {
      return id;
    }
    const item = {
      id,
      payload: Object.assign({}, payload, { _id: id }),
      createdAt: nowMs(),
      attempts: 0
    };
    items.push(item);
    while (items.length > config.maxItems) {
      const dropped = items.shift();
      logger.warn("[uni统计 2.0] retry queue overflow, drop oldest", dropped === null || dropped === void 0 ? void 0 : dropped.id);
    }
    writeQueue(items);
    return id;
  }
  function loadAll() {
    const items = readQueue();
    if (items.length === 0)
      return [];
    const cutoff = nowMs() - config.maxAgeMs;
    const alive = [];
    for (const it of items) {
      if (it.createdAt < cutoff) {
        logger.warn("[uni统计 2.0] retry item expired, drop", it.id);
        continue;
      }
      alive.push(it);
    }
    if (alive.length !== items.length)
      writeQueue(alive);
    return alive.map((it) => it.payload);
  }
  function ack(id) {
    if (!id)
      return;
    const items = readQueue();
    const next = items.filter((it) => it.id !== id);
    if (next.length === items.length)
      return;
    writeQueue(next);
  }
  function markAttempt(id) {
    if (!id)
      return;
    const items = readQueue();
    let nextItems = null;
    for (let i = 0; i < items.length; i++) {
      const it = items[i];
      if (it.id !== id)
        continue;
      it.attempts++;
      if (it.attempts >= config.maxAttempts) {
        logger.warn("[uni统计 2.0] retry item exceeded maxAttempts, drop as dead letter", id, "attempts=" + it.attempts);
        nextItems = items.slice(0, i).concat(items.slice(i + 1));
      } else {
        nextItems = items;
      }
      break;
    }
    if (nextItems)
      writeQueue(nextItems);
  }
  let instance = null;
  class StatApp {
    constructor() {
      this.installed = false;
      this.statVersion = "image";
    }
    static getInstance() {
      if (!instance)
        instance = new StatApp();
      return instance;
    }
    /**
     * 一次性装配。重复调用直接返回。
     *
     * @param config 业务配置；缺省值兼容私有版默认行为。
     * @param overrides 测试钩子。
     */
    install(config2 = {}, overrides = {}) {
      var _a, _b, _c, _d, _e;
      if (this.installed)
        return;
      const cfg = this.normalizeConfig(config2);
      this.config = cfg;
      this.statVersion = cfg.version;
      tryRun(() => configure$1({
        backgroundTimeoutSec: cfg.backgroundTimeoutSec,
        pageInactiveTimeoutSec: cfg.pageInactiveTimeoutSec
      }), void 0);
      tryRun(() => configure({ intervalSec: cfg.reportIntervalSec }), void 0);
      if (!overrides.skipMigration) {
        tryRun(() => migrateLegacyData(), false);
      }
      tryRun(() => loadVisitSnapshot(), void 0);
      this.httpChannel = (_b = (_a = overrides.channels) === null || _a === void 0 ? void 0 : _a.http) !== null && _b !== void 0 ? _b : createHttpChannel({ ut: getPlatform(), maxRetries: HTTP_MAX_RETRIES });
      if (overrides.channels && "cloud" in overrides.channels) {
        this.cloudChannel = (_c = overrides.channels.cloud) !== null && _c !== void 0 ? _c : void 0;
      } else if (this.statVersion === "2") {
        this.cloudChannel = createCloudChannel({ maxRetries: CLOUD_MAX_RETRIES });
      } else {
        this.cloudChannel = void 0;
      }
      if (overrides.channels && "image" in overrides.channels) {
        this.imageChannel = (_d = overrides.channels.image) !== null && _d !== void 0 ? _d : void 0;
      } else if (this.statVersion === "image") {
        this.imageChannel = createImageChannel({
          host: IMAGE_REPORT_DEFAULTS.host,
          projectId: IMAGE_REPORT_DEFAULTS.projectId,
          topicId: IMAGE_REPORT_DEFAULTS.topicId,
          maxRetries: IMAGE_MAX_RETRIES,
          ut: getPlatform(),
          rawPlatform: getRawPlatform()
        });
      } else {
        this.imageChannel = void 0;
      }
      this.collectorDeps = this.buildCollectorDeps(cfg, (_e = overrides.collectorDepsPatch) !== null && _e !== void 0 ? _e : {});
      this.collector = createCollector(this.collectorDeps);
      if (!overrides.skipInterceptors) {
        const c = this.collector;
        this.uninstallInterceptors = tryRun(() => installAllInterceptors({ report: (i) => c.report(i) }), void 0);
      }
      if (!overrides.skipRecoverRetry) {
        void this.collector.recoverRetry().catch((e) => logger.warn("[uni统计 2.0] recoverRetry failed", e));
      }
      this.uninstallNetworkWatch = tryRun(() => onNetworkOnline(() => {
        const c = this.collector;
        if (!c)
          return;
        void c.recoverRetry().catch((e) => logger.warn("[uni统计 2.0] recoverRetry on online failed", e));
        void c.flush(true).catch((e) => logger.warn("[uni统计 2.0] flush on online failed", e));
      }), void 0);
      this.installed = true;
    }
    /**
     * 业务侧 `uni.report(type, value)` 入口。
     *
     * 兼容私有版语义：
     *   - `type === 'title'` → 写 reportTitle，不发事件；下次 lt=11 / lt=3 携带 `ttc`。
     *   - 其他 type → 自定义事件 lt=21，custom `{ e_n: type, e_v: value }`。
     */
    report(type, value) {
      if (!this.installed || !this.collector)
        return;
      if (type === "title") {
        setReportTitle(value);
        return;
      }
      const ev = typeof value === "object" && value !== null ? tryRun(() => JSON.stringify(value), "") : value === void 0 ? "" : String(value);
      this.collector.report({
        lt: LT.Event,
        custom: { e_n: type, e_v: ev }
      });
    }
    /** 上报 onError 捕获的错误。 */
    reportError(err) {
      var _a;
      if (!this.installed || !this.collector)
        return;
      const errMsg2 = err instanceof Error ? `${err.name}: ${err.message}
${(_a = err.stack) !== null && _a !== void 0 ? _a : ""}` : typeof err === "string" ? err : tryRun(() => JSON.stringify(err), "");
      this.collector.report({ lt: LT.Error, errMsg: errMsg2 });
    }
    /** 取 collector，供 lifecycleHooks 调度生命周期事件。 */
    getCollector() {
      return this.collector;
    }
    /** 取 deps（测试用）。 */
    getDeps() {
      return this.collectorDeps;
    }
    /** 是否已 install。 */
    isInstalled() {
      return this.installed;
    }
    /** 当前协议版本。 */
    getStatVersion() {
      return this.statVersion;
    }
    /** 当前生效配置（含默认值合并），测试用。 */
    getConfig() {
      return this.config;
    }
    /**
     * 卸载（测试 / hot reload）。
     *
     * 解绑全部拦截器、清空内部句柄。**不**清外部模块（queue/visit/session）状态，
     * 那些由各自的 `__reset*` 在测试 setup 中处理。
     */
    uninstall() {
      if (this.uninstallInterceptors) {
        tryRun(() => this.uninstallInterceptors(), void 0);
      }
      this.uninstallInterceptors = void 0;
      if (this.uninstallNetworkWatch) {
        tryRun(() => this.uninstallNetworkWatch(), void 0);
      }
      this.uninstallNetworkWatch = void 0;
      if (this.collector) {
        tryRun(() => this.collector.destroy(), void 0);
      }
      this.collector = void 0;
      this.collectorDeps = void 0;
      this.httpChannel = void 0;
      this.cloudChannel = void 0;
      this.imageChannel = void 0;
      this.config = void 0;
      this.installed = false;
    }
    /**
     * 解析上行渠道字段 `ch`。
     *
     * App 渠道包标识只能以原生运行时为准：`plus.runtime.channel`。
     * `manifest.uniStatistics.ch` 是静态配置，不能区分同一项目打出的多渠道包。
     * 非 App 端没有 `plus.runtime.channel` 语义，保留手动 install 传入 `ch` 的能力。
     */
    resolveChannel(explicit) {
      if (isApp()) {
        return getAppChannel();
      }
      if (typeof explicit === "string" && explicit.length > 0) {
        return explicit;
      }
      return "";
    }
    resolveFirstFlushDeferMs() {
      if (getRawPlatform() === "mp-weixin" && MP_WEIXIN_USE_PRELOAD_ASSETS_REPORT) {
        return MP_WEIXIN_PRELOAD_FIRST_FLUSH_DELAY_MS;
      }
      if (isApp() && !getAppChannel()) {
        return APP_CHANNEL_FIRST_FLUSH_DELAY_MS;
      }
      return 0;
    }
    normalizeConfig(c) {
      var _a, _b, _c, _d;
      return {
        ak: (_a = c.ak) !== null && _a !== void 0 ? _a : getAppId$1(),
        v: c.v,
        ch: this.resolveChannel(c.ch),
        version: (_b = c.version) !== null && _b !== void 0 ? _b : "image",
        backgroundTimeoutSec: (_c = c.backgroundTimeoutSec) !== null && _c !== void 0 ? _c : 300,
        pageInactiveTimeoutSec: (_d = c.pageInactiveTimeoutSec) !== null && _d !== void 0 ? _d : 1800,
        reportIntervalSec: typeof c.reportIntervalSec === "number" ? c.reportIntervalSec : REPORT_INTERVAL_SEC,
        // collectItems 默认值与私有版严格对齐：push 默认关闭、页面日志默认开启
        enablePush: c.enablePush === true,
        enablePageLog: c.enablePageLog !== false
      };
    }
    /**
     * 构建 collector 依赖。所有 adapter 调用都包了 `tryRun`，避免单端缺失 API 导致
     * install 失败。
     */
    buildCollectorDeps(cfg, patch) {
      const platformShort = getPlatform();
      const builder = createStatDataBuilder({
        config: {
          ak: cfg.ak,
          usv: STAT_VERSION_PUBLIC,
          v: cfg.v,
          get ch() {
            return isApp() ? getAppChannel() : cfg.ch;
          }
        },
        platform: {
          ut: platformShort
        },
        system: tryRun(() => getSystemInfo(), {
          brand: "",
          md: "",
          sv: "",
          v: "",
          ut: "unknown",
          appVersion: "",
          appWgtVersion: "",
          mpvHostVersion: "",
          on: "",
          sdkVersion: "",
          statusBarHeight: 0,
          osP: ""
        }),
        locale: tryRun(() => getLocaleAndScreen(), {
          lang: "",
          ww: 0,
          wh: 0,
          sw: 0,
          sh: 0,
          pr: 1
        }),
        device: {
          // 惰性解析：每次 build 时再调 getUuid()，避免 install 过早（uni 运行时未就绪）冻结临时值。
          get uuid() {
            return tryRun(() => getUuid(), "");
          }
        },
        net: { net: "unknown", raw: "" },
        location: { lat: "", lng: "", ok: false },
        pkg: tryRun(() => getPackageInfo(), {
          mpn: "",
          tdaid: "",
          pkn: "",
          an: ""
        }),
        web: tryRun(() => getWebInfo(), { domain: "" })
      });
      const base = {
        builder,
        queue: {
          enqueue,
          flush,
          rollback,
          shouldFlush
        },
        serializer: { handleData },
        selectChannel: () => selectChannel({
          version: this.statVersion,
          http: this.httpChannel,
          cloud: this.cloudChannel,
          image: this.imageChannel
        }),
        retry: {
          persist,
          loadAll,
          ack,
          markAttempt
        },
        visit: {
          commitVisitOnAck,
          rollbackPendingVisit
        },
        session: {
          getSnapshot,
          nextSeq,
          touch
        },
        config: { usv: STAT_VERSION_PUBLIC },
        resolveUploadFields: () => {
          const ch = getAppChannel();
          return ch ? { ch } : {};
        },
        nowMs,
        nowSec,
        firstFlushDeferMs: this.resolveFirstFlushDeferMs(),
        isNetworkOffline
      };
      return Object.assign(base, patch);
    }
  }
  function getStatApp() {
    return StatApp.getInstance();
  }
  function parseInjectedUniStatistics() {
    const raw = "{}";
    const trimmed = raw.trim();
    if (!trimmed || trimmed === "undefined")
      return void 0;
    try {
      const obj = JSON.parse(trimmed);
      if (!obj || typeof obj !== "object" || Array.isArray(obj))
        return void 0;
      return obj;
    } catch (_e) {
      return void 0;
    }
  }
  function readManifestStatConfig() {
    try {
      const obj = parseInjectedUniStatistics();
      if (!obj)
        return void 0;
      const cfg = {};
      if (obj.channelVersion != null) {
        const v = String(obj.channelVersion);
        if (v === "1" || v === "2" || v === "image")
          cfg.version = v;
      }
      const bg = pickPositiveNumber(obj.backgroundTimeout, obj.backgroundTimeoutSec);
      if (bg !== void 0)
        cfg.backgroundTimeoutSec = bg;
      const pi = pickPositiveNumber(obj.pageInactiveTimeout, obj.pageInactiveTimeoutSec);
      if (pi !== void 0)
        cfg.pageInactiveTimeoutSec = pi;
      const ri = pickNonNegativeNumber(obj.reportInterval, obj.reportIntervalSec);
      if (ri !== void 0)
        cfg.reportIntervalSec = ri;
      if (obj.collectItems && typeof obj.collectItems === "object") {
        const items = obj.collectItems;
        if (typeof items.uniPushClientID === "boolean") {
          cfg.enablePush = items.uniPushClientID;
        }
        if (typeof items.uniStatPageLog === "boolean") {
          cfg.enablePageLog = items.uniStatPageLog;
        }
      }
      return Object.keys(cfg).length > 0 ? cfg : void 0;
    } catch (e) {
      logger.warn("[uni统计 2.0] readManifestStatConfig failed", e);
      return void 0;
    }
  }
  function normalizePositiveNumber(value) {
    if (typeof value === "number") {
      return value > 0 ? value : void 0;
    }
    if (typeof value === "string") {
      const t = value.trim();
      if (t === "")
        return void 0;
      const n2 = Number(t);
      if (Number.isFinite(n2) && n2 > 0)
        return n2;
    }
    return void 0;
  }
  function normalizeNonNegativeNumber(value) {
    if (typeof value === "number") {
      return value >= 0 ? value : void 0;
    }
    if (typeof value === "string") {
      const t = value.trim();
      if (t === "")
        return void 0;
      const n2 = Number(t);
      if (Number.isFinite(n2) && n2 >= 0)
        return n2;
    }
    return void 0;
  }
  function pickPositiveNumber(...candidates) {
    for (const c of candidates) {
      const n2 = normalizePositiveNumber(c);
      if (n2 !== void 0)
        return n2;
    }
    return void 0;
  }
  function pickNonNegativeNumber(...candidates) {
    for (const c of candidates) {
      const n2 = normalizeNonNegativeNumber(c);
      if (n2 !== void 0)
        return n2;
    }
    return void 0;
  }
  function getUni() {
    const u = resolveUniRuntime();
    return u != null && typeof u === "object" ? u : void 0;
  }
  const UNI_HOOK_RETRY_MAX = 20;
  const UNI_HOOK_RETRY_MS = 50;
  let vueMixinMounted = false;
  let vueMixinRetryTimer;
  let bootstrapped = false;
  let uniHookRetryTimer;
  function installPublicStat(opts = {}) {
    if (bootstrapped)
      return;
    bootstrapped = true;
    const fromManifest = readManifestStatConfig();
    const finalConfig = Object.assign({}, fromManifest, opts.config);
    const app = getStatApp();
    tryRun(() => app.install(finalConfig, opts.overrides), void 0);
    tryRun(() => {
      var _a, _b, _c;
      const cfgBoot = app.getConfig();
      const appName = "鹊桥 · 红娘工作台";
      const injected = parseInjectedUniStatistics();
      const bootBase = {
        channel: (_a = cfgBoot === null || cfgBoot === void 0 ? void 0 : cfgBoot.version) !== null && _a !== void 0 ? _a : "image",
        reportIntervalSec: (_b = cfgBoot === null || cfgBoot === void 0 ? void 0 : cfgBoot.reportIntervalSec) !== null && _b !== void 0 ? _b : 0,
        ak: (_c = cfgBoot === null || cfgBoot === void 0 ? void 0 : cfgBoot.ak) !== null && _c !== void 0 ? _c : "",
        appName,
        debugFromManifest: "false" === true
      };
      if (injected != null) {
        if (injected.backgroundTimeout != null || injected.backgroundTimeoutSec != null) {
          bootBase.backgroundTimeoutSec = cfgBoot === null || cfgBoot === void 0 ? void 0 : cfgBoot.backgroundTimeoutSec;
        }
        if (injected.pageInactiveTimeout != null || injected.pageInactiveTimeoutSec != null) {
          bootBase.pageInactiveTimeoutSec = cfgBoot === null || cfgBoot === void 0 ? void 0 : cfgBoot.pageInactiveTimeoutSec;
        }
      }
      logBoot(Object.assign({}, bootBase, { vueMode: "Vue3" }));
    }, void 0);
    const finishLifecycleInstall = () => {
      var _a, _b;
      const cfg = app.getConfig();
      const lifecycleOpts = Object.assign({}, {
        enablePush: (_a = cfg === null || cfg === void 0 ? void 0 : cfg.enablePush) !== null && _a !== void 0 ? _a : false,
        enablePageLog: (_b = cfg === null || cfg === void 0 ? void 0 : cfg.enablePageLog) !== null && _b !== void 0 ? _b : true
      }, opts.lifecycle);
      const { mixin } = bindLifecycle(app, lifecycleOpts);
      if (!opts.skipVueMixin) {
        tryRun(() => mountVueMixin(mixin), void 0);
      }
      if (!opts.skipUniReport) {
        tryRun(() => mountUniReport(app), void 0);
      }
      if (shouldBindUniAppLifecycle() && !tryBindUniAppLifecycle(app, lifecycleOpts)) {
        scheduleUniAppHookRetry(() => tryBindUniAppLifecycle(app, lifecycleOpts));
      }
    };
    finishLifecycleInstall();
  }
  function scheduleUniAppHookRetry(tryBind) {
    if (uniHookRetryTimer) {
      clearTimeout(uniHookRetryTimer);
      uniHookRetryTimer = void 0;
    }
    let attempts = 0;
    const tick = () => {
      if (tryBind())
        return;
      if (++attempts >= UNI_HOOK_RETRY_MAX) {
        logger.warn("[uni统计 2.0] Vue3 小程序：uni.onAppShow 暂不可用，应用前后台统计可能缺失");
        return;
      }
      uniHookRetryTimer = setTimeout(tick, UNI_HOOK_RETRY_MS);
    };
    uniHookRetryTimer = setTimeout(tick, UNI_HOOK_RETRY_MS);
  }
  function tryRegisterVueAppMixin(mixin) {
    try {
      ;
      uni.onCreateVueApp((vueApp) => {
        tryRun(() => vueApp.mixin(mixin), void 0);
      });
      return true;
    } catch (_e) {
    }
    const u = getUni();
    if (u && typeof u.onCreateVueApp === "function") {
      u.onCreateVueApp((vueApp) => {
        tryRun(() => vueApp.mixin(mixin), void 0);
      });
      return true;
    }
    return false;
  }
  function mountVueMixin(mixin) {
    if (vueMixinMounted)
      return;
    if (tryRegisterVueAppMixin(mixin)) {
      vueMixinMounted = true;
      return;
    }
    scheduleVueAppMixinRetry(mixin);
  }
  function scheduleVueAppMixinRetry(mixin) {
    if (vueMixinMounted)
      return;
    if (vueMixinRetryTimer)
      return;
    let attempts = 0;
    const tick = () => {
      vueMixinRetryTimer = void 0;
      if (vueMixinMounted)
        return;
      if (tryRegisterVueAppMixin(mixin)) {
        vueMixinMounted = true;
        return;
      }
      if (++attempts >= UNI_HOOK_RETRY_MAX) {
        if (!vueMixinMounted) {
          logger.warn("[uni统计 2.0] Vue3: onCreateVueApp 在重试后仍不可用，页面级 mixin 未注入");
        }
        return;
      }
      vueMixinRetryTimer = setTimeout(tick, UNI_HOOK_RETRY_MS);
    };
    vueMixinRetryTimer = setTimeout(tick, UNI_HOOK_RETRY_MS);
  }
  function mountUniReport(app) {
    var _a;
    const g = getGlobalObject();
    const u = (_a = getUni()) !== null && _a !== void 0 ? _a : g.uni;
    if (!u || typeof u !== "object")
      return;
    u.report = (type, value) => {
      app.report(type, value);
    };
  }
  installPublicStat();
  function createApp() {
    const app = vue.createVueApp(App);
    return {
      app
    };
  }
  const { app: __app__, Vuex: __Vuex__, Pinia: __Pinia__ } = createApp();
  uni.Vuex = __Vuex__;
  uni.Pinia = __Pinia__;
  __app__.provide("__globalStyles", __uniConfig.styles);
  __app__._component.mpType = "app";
  __app__._component.render = () => {
  };
  __app__.mount("#app");
})(Vue);
