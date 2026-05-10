import React, { createContext, useContext, useState, ReactNode } from 'react';

export type Language = 'en' | 'zh';

const translations = {
  zh: {
    common: {
      add: '添加',
      edit: '编辑',
      delete: '删除',
      save: '保存更改',
      cancel: '取消',
      clearAll: '清空全部',
      selectAll: '全选',
      search: '搜索',
      filter: '筛选',
      export: '导出',
      view: '查看',
      status: '状态',
      latency: '延迟',
      apiKey: 'API 密钥',
      keys: '密钥',
      timeout: '超时',
      details: '详情',
      confirmDelete: '确认删除？此操作无法撤销。',
      active: '正常',
      inactive: '停用',
      enabled: '已开启',
      disabled: '已关闭',
      degraded: '降级',
      logout: '退出登录',
      copy: '复制',
      close: '关闭',
      success: '成功',
      fail: '失败',
      copied: '已复制'
    },
    nav: {
      dashboard: '仪表盘',
      providers: '供应商管理',
      groups: '分组管理',
      pricing: '价格管理',
      logs: '日志系统',
      settings: '设置中心',
      systemHealthy: '系统正常',
    },
    login: {
      nav: {
        login: '登录',
        getStarted: '开始使用'
      },
      hero: {
        tagline: 'Lumina 开源网关 v{{version}} 已发布',
        titleLine1: '开源',
        titleLine2: 'AI 模型',
        titleLine3: '网关',
        subtitle: '支持 OpenAI、Anthropic、Gemini 统一中继。内置熔断机制与智能故障转移，支持 Docker 开箱即用。',
        startBtn: '立即开始',
        docBtn: '查看文档'
      },
      features: {
        title: '核心功能',
        subtitle: '提供 AI 模型中继的基础能力。',
        unifiedGateway: {
          title: '统一 API 中继',
          desc: '支持 OpenAI, Anthropic, Gemini 标准接口中继，涵盖 Chat, Messages 及 Models API，支持流式响应。'
        },
        loadBalancing: {
          title: '智能故障转移',
          desc: '基于 Top-K Softmax 算法的加权选择，支持连接超时、限流等场景的自动重试与故障切换。'
        },
        security: {
          title: '认证与鉴权',
          desc: '管理后台采用 JWT 安全认证，API 调用支持多维度 Key 管理及启用/禁用控制。'
        },
        observability: {
          title: '可观测性',
          desc: '实时监控请求流量、Token 消耗及费用统计。提供完整的请求日志记录与熔断器状态监控。'
        },
        modelMapping: {
          title: '模型分组',
          desc: '支持模型名称精确匹配自动路由，分组内可配置多个 Provider 作为备份。'
        },
        circuitBreaking: {
          title: '熔断机制',
          desc: '基于错误率与慢调用的多维触发，支持指数退避自愈探测 (Half-Open) 与并发控制 (Bulkhead)。'
        }
      },
      footer: {
        text: '开源 AI 模型网关。',
        rights: '保留所有权利。'
      },
      modal: {
        welcome: '欢迎回来',
        signinDesc: '登录以管理您的 AI 网关',
        username: '用户名',
        password: '密码',
        signinBtn: '登录',
        authenticating: '验证中...',
        systemInfo: 'Lumina Gateway System • 仅限授权访问',
        placeholderUser: '请输入用户名',
        placeholderPass: '请输入密码'
      },
      errors: {
          missingCreds: '请输入用户名和密码。',
          failed: '登录失败。请检查您的凭据。',
          network: '无法连接到服务器。请检查您的网络连接。'
      }
    },
    dashboard: {
      title: '仪表盘概览',
      subtitle: 'LLM 网关实时监控',
      totalRequests: '总请求数',
      totalTokens: '总 Token 数',
      totalCost: '预估总费用',
      avgLatency: '平均延迟',
      successRate: '成功率',
      traffic: '请求流量 (24小时)',
      tokenUsage: '模型 Token 使用分布',
      recentActivity: '近期 API 调用',
      viewLogs: '查看所有日志',
      viewProviders: '管理供应商',
      table: {
        timestamp: '时间戳',
        model: '模型',
        status: '状态',
        latency: '延迟',
        cost: '费用',
      },
      ranking: {
        title: '供应商统计排名',
        noData: '暂无排名数据',
        options: {
          calls: '调用次数',
          cost: '预估费用',
          latency: '平均延迟',
          successRate: '成功率',
        },
        columns: {
           rank: '排名',
           provider: '供应商名称',
           status: '状态'
        },
        status: {
           observation: '观测中',
           normal: '正常',
           active: '活跃',
           excellent: '优秀',
           slow: '偏慢',
           abnormal: '异常',
           volatile: '波动'
        }
      },
      observability: {
        title: '运行态观测',
        subtitle: '展示缓存命中、故障切换、舱壁保护与日志背压的实时状态。',
        autoRefresh: '每 {{seconds}} 秒刷新 · 最近更新 {{time}}',
        cards: {
          cacheHitRate: '缓存命中率',
          providersTrackedSuffix: '个 Provider',
          openCircuits: 'OPEN / HALF_OPEN',
          openCircuitsDetail: '当前处于保护状态的 Provider',
          failoverSwitches: '故障切换次数',
          failoverTerminationsSuffix: '次终止',
          bulkheadRejections: 'Bulkhead 拒绝',
          bulkheadRejectionsDetail: '触发并发舱壁保护的请求数',
          logQueue: '日志队列 / 丢弃',
        },
        cache: {
          title: '热路径缓存',
          columns: {
            name: '缓存项',
            hitRate: '命中率',
            lookups: '查询数',
            loads: '加载数',
            avgLoadMs: '平均加载'
          },
          names: {
            group_config: '分组配置',
            api_key: 'API Key',
            model_price: '模型价格'
          }
        },
        routing: {
          title: '路由与切换',
          items: {
            saprSelections: 'SAPR 选择',
            roundRobinSelections: '轮询选择',
            fallbackToRoundRobin: '降级到轮询',
            failoverAttempts: 'Failover 尝试',
            failoverDepthAvg: '平均切换深度'
          }
        },
        pipeline: {
          title: '后台管线',
          items: {
            logDropped: '日志丢弃总数',
            logQueue: '日志队列深度',
            logFlushAvgMs: '日志刷盘耗时',
            logBatchAvg: '平均批大小',
            bulkheadRejected: 'Bulkhead 拒绝总数'
          }
        },
        providers: {
          title: 'Provider 运行态',
          noData: '暂无运行态数据',
          filters: {
            provider: '供应商筛选',
            model: '模型筛选',
            state: '状态筛选',
            allProviders: '全部供应商',
            allModels: '全部模型',
            allStates: '全部状态'
          },
          columns: {
            provider: 'Provider',
            model: '模型',
            state: '状态',
            score: '健康分',
            totalRequests: '总请求',
            successRequests: '成功请求',
            failureRequests: '失败请求',
            successRate: '成功率',
            concurrent: '并发占用',
            rejected: '拒绝数',
            nextProbe: '下次探测'
          }
        }
      }
    },
    providers: {
      title: '供应商管理',
      subtitle: '管理上游供应商及 API 密钥',
      addProvider: '添加供应商',
      provider: '供应商',
      baseUrl: 'API 地址',
      noProviders: '暂无供应商',
      addFirst: '添加第一个供应商以开始路由请求',
      deleteTitle: '删除供应商？',
      deleteDesc: '您确定要删除 {{name}} 吗？此操作无法撤销。',
      deletedSuccess: '供应商已删除',
      updatedSuccess: '供应商已更新',
      createdSuccess: '供应商已创建',
      failed: '操作失败',
      latencyResult: '连接正常。延迟: {{latency}}ms',
      syncing: '正在同步模型...',
      syncedSuccess: '成功同步 {{count}} 个模型',
      modal: {
        titleAdd: '添加供应商',
        titleEdit: '编辑供应商',
        name: '名称',
        type: '类型',
        baseUrl: 'API 基础地址',
        apiKey: 'API 密钥',
        apiKeyPlaceholder: '请输入 API 密钥 (sk-...)',
        models: '可用模型',
        modelsPlaceholder: 'gpt-4, gpt-3.5-turbo (逗号分隔)',
        status: '当前状态',
        autoSync: '模型自动同步'
      },
      more: {
        testConnection: '测试连接',
        syncModels: '同步模型列表'
      },
      validation: {
        name: '请输入供应商名称',
        baseUrl: '请输入API基础地址',
        apiKey: '请输入API密钥',
        models: '请至少输入一个模型名称'
      }
    },
    groups: {
      title: '分组与路由',
      subtitle: '配置负载均衡策略与路由规则',
      createGroup: '新建分组',
      activeProviders: '活跃供应商',
      noGroups: '暂无分组',
      createFirst: '创建一个分组来路由请求',
      deleteTitle: '删除分组？',
      deleteDesc: '您确定要删除 {{name}} 吗？此操作无法撤销。',
      modal: {
        titleAdd: '新建分组',
        titleEdit: '编辑分组',
        name: '分组名称',
        mode: '负载均衡模式',
        timeout: '超时设置 (ms)',
        selectedProviders: '包含的模型与供应商',
        selectPlaceholder: '请选择...',
        searchModels: '搜索模型...',
        viewSelected: '仅查看已选',
        invalidSelections: '无效或缺失的模型选择',
        noModelsSelected: '未选择任何模型',
        noActiveProviders: '无活跃供应商'
      },
      modes: {
        roundRobin: '轮询 (Round Robin)',
        random: '随机 (Random)',
        failover: '故障转移 (Failover)',
        weighted: '加权 (Weighted)',
        sapr: '自适应 (SAPR)'
      }
    },
    pricing: {
      title: '模型价格',
      subtitle: '仅显示分组内使用的模型，自动从 models.dev 同步模型价格数据。',
      searchPlaceholder: '搜索模型名称...',
      sync: '同步上游模型',
      syncSuccess: '模型同步成功',
      syncFail: '模型同步失败',
      noModels: '未找到模型',
      adjustSearch: '请尝试调整搜索条件',
      table: {
        modelName: '模型名称',
        provider: '供应商',
        inputPrice: '输入价格 (1M)',
        outputPrice: '输出价格 (1M)',
        context: '上下文 / 输出限制',
        capabilities: '能力',
        updated: '更新时间',
        inputType: '输入类型'
      },
      capabilities: {
        reasoning: '推理',
        toolCall: '工具调用'
      }
    },
    logs: {
      title: '请求日志',
      subtitle: '搜索与分析 API 请求历史',
      searchPlaceholder: '搜索请求 ID、模型或路径...',
      autoRefresh: '自动刷新',
      refreshInterval: '刷新间隔',
      seconds: '秒',
      noLogs: '暂无日志',
      noLogsDesc: '请尝试调整筛选条件或刷新',
      table: {
        status: '状态',
        time: '时间',
        requestModel: '请求模型',
        actualModel: '实际模型',
        provider: '供应商',
        latency: '延迟',
        tokens: 'Token数',
        cost: '费用',
      },
      pagination: {
        showing: '显示',
        to: '到',
        of: '共',
        results: '条结果',
        prev: '上一页',
        next: '下一页',
      },
      detail: {
        title: '请求详情',
        loading: '正在加载详情...',
        failed: '加载详情失败。',
        info: '基本信息',
        performance: '性能与费用',
        content: '请求内容',
        responseContent: '响应内容',
        loadRequestContent: '加载请求内容',
        loadResponseContent: '加载响应内容',
        loadingPayload: '正在加载内容...',
        payloadHint: '请求与响应正文按需加载，避免详情接口拉取超大文本。',
        noRequestContent: '暂无请求内容',
        noResponseContent: '暂无响应内容',
        error: '错误信息',
        requestId: '请求 ID',
        provider: '供应商',
        type: '请求类型',
        stream: '流式响应',
        created: '创建时间',
        retry: '重试次数',
        duration: '总耗时'
      }
    },
    settings: {
      title: '设置中心',
      subtitle: '系统配置与偏好设置',
      language: '语言设置',
      languageDesc: '选择系统显示语言',
      flushNow: '立即刷新',
      security: '客户端访问令牌',
      securityDesc: '管理应用程序用于通过 Lumina 进行身份验证的令牌。',
      manageTokens: '管理令牌',
      appearance: '外观设置',
      appearanceDesc: '自定义仪表盘的外观风格。',
      light: '浅色',
      dark: '深色',
      account: '账号设置',
      accountDesc: '更新管理员用户名和密码。修改后需要重新登录。',
      username: '用户名',
      originalPassword: '原密码',
      password: '新密码',
      confirmPassword: '确认新密码',
      leaveBlank: '若不修改请留空',
      updateSuccess: '账号信息更新成功，即将跳转登录页...',
      passwordsDoNotMatch: '两次输入的密码不一致',
      update: '更新账号信息',
      selfUseMode: {
        title: '自用模式',
        desc: '开启后，所有访问 /v1/** 与 /v1beta/** 的请求将跳过 API Key 校验，可直接调用。适合部署在完全受信任的本地或内网环境下自用。',
        warning: '安全提示：自用模式会关闭 API Key 鉴权，切勿在公网可访问的部署中开启。一旦关闭将立即恢复鉴权。',
        activeBadge: '已开启',
        enabled: '自用模式已开启',
        disabled: '自用模式已关闭',
      },
      tokens: {
        title: 'API 令牌管理',
        create: '创建新令牌',
        name: '令牌名称',
        key: 'Secret Key',
        created: '创建时间',
        lastUsed: '上次使用',
        empty: '暂无可用令牌',
        copied: '已复制',
        generatedSuccess: '令牌生成成功',
        generateAnother: '生成另一个令牌',
        activeTokens: '活跃令牌',
        confirmRevokeShort: '确认撤销？',
        revokeSuccess: '令牌已成功撤销',
        copyWarning: '请务必立即复制此令牌。出于安全原因，它将不会再次显示。',
        confirmRevoke: '确定要撤销此令牌吗？任何使用此令牌的应用程序将立即无法访问。'
      },
      circuitBreaker: {
        title: '熔断器管控',
        desc: '以 Provider + Model 粒度查看熔断状态、触发原因、配置来源与人工干预历史。',
        refresh: '刷新状态',
        updated: '熔断器状态已更新',
        released: '手动控制已释放',
        targetProvider: '目标供应商',
        loading: '加载中...',
        empty: '暂无熔断器运行态数据',
        recentEvents: '最近人工操作',
        noRecentEvents: '暂无人工控制事件',
        filters: {
          provider: '供应商',
          model: '模型',
          state: '状态',
          source: '配置来源',
          manual: '控制模式',
          allProviders: '全部供应商',
          allModels: '全部模型',
          allStates: '全部状态',
          allSources: '全部来源',
          allModes: '全部模式',
          manualOnly: '仅手动控制',
          autoOnly: '仅自动'
        },
        table: {
          provider: '供应商',
          model: '模型',
          state: '熔断状态',
          since: '状态开始',
          nextProbe: '下次探测',
          score: '健康分',
          requests: '请求量',
          concurrency: '并发占用',
          source: '配置来源',
          stats: '统计 (错误/慢调用)',
          control: '手动管控'
        },
        details: {
          explanation: '状态解释',
          failureType: '最近失败类型',
          groups: '关联分组',
          thresholds: '当前阈值',
          manualReason: '人工原因',
          noReason: '无',
          mixed: '混合配置上下文'
        },
        states: {
          CLOSED: '正常 (CLOSED)',
          OPEN: '熔断 (OPEN)',
          HALF_OPEN: '探测 (HALF_OPEN)'
        },
        sources: {
          global: '全局',
          group: '分组',
          provider: 'Provider',
          mixed: '混合'
        },
        controlModal: {
          title: '手动控制熔断器',
          targetState: '目标状态',
          reason: '操作原因',
          reasonPlaceholder: '请输入操作原因 (用于审计)',
          duration: '熔断持续时间 (毫秒)',
          confirm: '确认操作',
          manualActive: '手动控制中',
          currentState: '当前状态',
          currentScore: '当前健康分',
          currentLoad: '当前并发',
          warningOpen: '将强制打开熔断器，Provider 会在设定时间内保持不可用。',
          warningHalfOpen: '将强制进入 HALF_OPEN，仅允许探测流量通过。',
          warningClosed: '将强制关闭熔断器并清空当前探测/熔断计数。',
          releaseHint: '当前实例处于手动控制状态，建议先释放控制再重新设定。'
        },
        actions: {
          manage: '管控',
          release: '释放控制',
          details: '详情'
        },
        events: {
          control: '手动控制',
          release: '释放控制'
        }
      }
    }
  },
  en: {
    common: {
      add: 'Add',
      edit: 'Edit',
      delete: 'Delete',
      save: 'Save Changes',
      cancel: 'Cancel',
      clearAll: 'Clear All',
      selectAll: 'Select All',
      search: 'Search',
      filter: 'Filter',
      export: 'Export',
      view: 'View',
      status: 'Status',
      latency: 'Latency',
      apiKey: 'API Key',
      keys: 'Keys',
      timeout: 'timeout',
      details: 'Details',
      confirmDelete: 'Are you sure you want to delete this provider? This action cannot be undone.',
      active: 'Active',
      inactive: 'Inactive',
      enabled: 'Enabled',
      disabled: 'Disabled',
      degraded: 'Degraded',
      logout: 'Logout',
      copy: 'Copy',
      close: 'Close',
      success: 'Success',
      fail: 'Failure',
      copied: 'Copied'
    },
    nav: {
      dashboard: 'Dashboard',
      providers: 'Providers',
      groups: 'Groups',
      pricing: 'Pricing',
      logs: 'Logs',
      settings: 'Settings',
      systemHealthy: 'System Healthy',
    },
    login: {
      nav: {
        login: 'Login',
        getStarted: 'Get Started'
      },
      hero: {
        tagline: 'Lumina Open Source Gateway v{{version}}',
        titleLine1: 'Open Source',
        titleLine2: 'AI Model',
        titleLine3: 'Gateway',
        subtitle: 'Unified relay for OpenAI, Anthropic, Gemini. Built-in circuit breaking and smart failover. Docker ready out of the box.',
        startBtn: 'Start Using Now',
        docBtn: 'View Documentation'
      },
      features: {
        title: 'Core Features',
        subtitle: 'Essential capabilities for AI model relay.',
        unifiedGateway: {
          title: 'Unified API Relay',
          desc: 'Supports standard OpenAI, Anthropic, and Gemini formats including Chat, Messages, and Models APIs with streaming.'
        },
        loadBalancing: {
          title: 'Smart Failover',
          desc: 'Top-K Softmax weighted selection with automatic retry and failover for timeouts, rate limits, and errors.'
        },
        security: {
          title: 'Authentication',
          desc: 'Secure JWT for admin dashboard. API Keys support multi-dimensional management and enable/disable controls.'
        },
        observability: {
          title: 'Observability',
          desc: 'Real-time monitoring of traffic, token usage, and costs. Complete request logging and circuit breaker status monitoring.'
        },
        modelMapping: {
          title: 'Model Grouping',
          desc: 'Supports automatic routing via exact model name matching. Configure multiple Providers within a group for backup.'
        },
        circuitBreaking: {
          title: 'Circuit Breaking',
          desc: 'Multi-dimensional triggers based on error rates and slow calls. Supports exponential backoff self-healing and Bulkhead concurrency control.'
        }
      },
      footer: {
        text: 'Open Source AI Model Gateway.',
        rights: 'All rights reserved.'
      },
      modal: {
        welcome: 'Welcome Back',
        signinDesc: 'Sign in to manage your AI Gateway',
        username: 'Username',
        password: 'Password',
        signinBtn: 'Sign In',
        authenticating: 'Authenticating...',
        systemInfo: 'Lumina Gateway System • Restricted Access',
        placeholderUser: 'admin',
        placeholderPass: '••••••••'
      },
      errors: {
          missingCreds: 'Please enter both username and password.',
          failed: 'Login failed. Please check your credentials.',
          network: 'Unable to connect to the server. Please check your network connection.'
      }
    },
    dashboard: {
      title: 'Dashboard Overview',
      subtitle: 'Real-time monitoring of your LLM gateway.',
      totalRequests: 'Total Requests',
      totalTokens: 'Total Tokens',
      totalCost: 'Total Cost (Est.)',
      avgLatency: 'Avg. Latency',
      successRate: 'Success Rate',
      traffic: 'Request Traffic (24h)',
      tokenUsage: 'Token Usage by Model',
      recentActivity: 'Recent API Calls',
      viewLogs: 'View All Logs',
      viewProviders: 'Manage Providers',
      table: {
        timestamp: 'Timestamp',
        model: 'Model',
        status: 'Status',
        latency: 'Latency',
        cost: 'Cost',
      },
      ranking: {
        title: 'Provider Statistics Ranking',
        noData: 'No data available for ranking',
        options: {
          calls: 'Calls',
          cost: 'Est. Cost',
          latency: 'Avg. Latency',
          successRate: 'Success Rate',
        },
        columns: {
           rank: 'Rank',
           provider: 'Provider Name',
           status: 'Status'
        },
        status: {
           observation: 'Observation',
           normal: 'Normal',
           active: 'Active',
           excellent: 'Excellent',
           slow: 'Slow',
           abnormal: 'Abnormal',
           volatile: 'Volatile'
        }
      },
      observability: {
        title: 'Runtime Observability',
        subtitle: 'Live visibility into cache hits, failover behavior, bulkhead protection, and log backpressure.',
        autoRefresh: 'Refresh every {{seconds}}s · last update {{time}}',
        cards: {
          cacheHitRate: 'Cache Hit Rate',
          providersTrackedSuffix: 'providers tracked',
          openCircuits: 'OPEN / HALF_OPEN',
          openCircuitsDetail: 'Providers currently under protection',
          failoverSwitches: 'Failover Switches',
          failoverTerminationsSuffix: 'terminations',
          bulkheadRejections: 'Bulkhead Rejections',
          bulkheadRejectionsDetail: 'Requests rejected by concurrency protection',
          logQueue: 'Log Queue / Dropped',
        },
        cache: {
          title: 'Hot Path Caches',
          columns: {
            name: 'Cache',
            hitRate: 'Hit Rate',
            lookups: 'Lookups',
            loads: 'Loads',
            avgLoadMs: 'Avg Load'
          },
          names: {
            group_config: 'Group Config',
            api_key: 'API Key',
            model_price: 'Model Price'
          }
        },
        routing: {
          title: 'Routing & Failover',
          items: {
            saprSelections: 'SAPR Selections',
            roundRobinSelections: 'Round-Robin Selections',
            fallbackToRoundRobin: 'Fallback to Round-Robin',
            failoverAttempts: 'Failover Attempts',
            failoverDepthAvg: 'Avg Failover Depth'
          }
        },
        pipeline: {
          title: 'Background Pipelines',
          items: {
            logDropped: 'Dropped Logs',
            logQueue: 'Log Queue Depth',
            logFlushAvgMs: 'Log Flush Time',
            logBatchAvg: 'Avg Batch Size',
            bulkheadRejected: 'Bulkhead Rejections'
          }
        },
        providers: {
          title: 'Provider Runtime State',
          noData: 'No runtime data available',
          filters: {
            provider: 'Provider Filter',
            model: 'Model Filter',
            state: 'State Filter',
            allProviders: 'All Providers',
            allModels: 'All Models',
            allStates: 'All States'
          },
          columns: {
            provider: 'Provider',
            model: 'Model',
            state: 'State',
            score: 'Score',
            totalRequests: 'Total Requests',
            successRequests: 'Success Requests',
            failureRequests: 'Failure Requests',
            successRate: 'Success Rate',
            concurrent: 'Concurrency',
            rejected: 'Rejected',
            nextProbe: 'Next Probe'
          }
        }
      }
    },
    providers: {
      title: 'Providers',
      subtitle: 'Manage upstream providers and API keys.',
      addProvider: 'Add Provider',
      provider: 'Provider',
      baseUrl: 'Base URL',
      noProviders: 'No providers found',
      addFirst: 'Add a new provider to start routing requests',
      deleteTitle: 'Delete Provider?',
      deleteDesc: 'Are you sure you want to delete {{name}}? This action cannot be undone.',
      deletedSuccess: 'Provider deleted successfully',
      updatedSuccess: 'Provider updated successfully',
      createdSuccess: 'Provider created successfully',
      failed: 'Operation failed',
      latencyResult: 'Connection active. Latency: {{latency}}ms',
      syncing: 'Syncing models...',
      syncedSuccess: 'Synced {{count}} models',
      modal: {
        titleAdd: 'Add Provider',
        titleEdit: 'Edit Provider',
        name: 'Name',
        type: 'Type',
        baseUrl: 'Base URL',
        apiKey: 'API Key',
        apiKeyPlaceholder: 'Enter API Key (sk-...)',
        models: 'Available Models',
        modelsPlaceholder: 'gpt-4, gpt-3.5-turbo (comma separated)',
        status: 'Status',
        autoSync: 'Model Auto Sync'
      },
      more: {
        testConnection: 'Test Connection',
        syncModels: 'Sync Models'
      },
      validation: {
        name: 'Provider name is required',
        baseUrl: 'Base URL is required',
        apiKey: 'API Key is required',
        models: 'At least one model is required'
      }
    },
    groups: {
      title: 'Groups & Routing',
      subtitle: 'Configure load balancing strategies and routing rules.',
      createGroup: 'Create Group',
      activeProviders: 'Active Providers',
      noGroups: 'No groups found',
      createFirst: 'Create a group to route requests',
      deleteTitle: 'Delete Group?',
      deleteDesc: 'Are you sure you want to delete {{name}}? This action cannot be undone.',
      modal: {
        titleAdd: 'Create Group',
        titleEdit: 'Edit Group',
        name: 'Group Name',
        mode: 'Load Balance Mode',
        timeout: 'Timeout (ms)',
        selectedProviders: 'Included Models & Providers',
        selectPlaceholder: 'Select...',
        searchModels: 'Search models...',
        viewSelected: 'View Selected',
        invalidSelections: 'Invalid / Missing Selections',
        noModelsSelected: 'No models selected',
        noActiveProviders: 'No active providers'
      },
      modes: {
        roundRobin: 'Round Robin',
        random: 'Random',
        failover: 'Failover',
        weighted: 'Weighted',
        sapr: 'SAPR'
      }
    },
    pricing: {
      title: 'Model Pricing',
      subtitle: 'Automatically sync model pricing data from models.dev.',
      searchPlaceholder: 'Search model name...',
      sync: 'Sync Upstream Models',
      syncSuccess: 'Models synced successfully',
      syncFail: 'Failed to sync models',
      noModels: 'No models found',
      adjustSearch: 'Try adjusting your search criteria',
      table: {
        modelName: 'Model Name',
        provider: 'Provider',
        inputPrice: 'Input Price (1M)',
        outputPrice: 'Output Price (1M)',
        context: 'Context / Output Limit',
        capabilities: 'Capabilities',
        updated: 'Updated',
        inputType: 'Input Type'
      },
      capabilities: {
        reasoning: 'Reasoning',
        toolCall: 'Tool Call'
      }
    },
    logs: {
      title: 'Request Logs',
      subtitle: 'Search and analyze API request history.',
      searchPlaceholder: 'Search by Request ID, Model, or Path...',
      autoRefresh: 'Auto Refresh',
      refreshInterval: 'Interval',
      seconds: 's',
      noLogs: 'No logs found',
      noLogsDesc: 'Try adjusting filters or refreshing',
      table: {
        status: 'Status',
        time: 'Time',
        requestModel: 'Request Model',
        actualModel: 'Actual Model',
        provider: 'Provider',
        latency: 'Latency',
        tokens: 'Tokens',
        cost: 'Cost',
      },
      pagination: {
        showing: 'Showing',
        to: 'to',
        of: 'of',
        results: 'results',
        prev: 'Previous',
        next: 'Next',
      },
      detail: {
        title: 'Request Detail',
        loading: 'Loading details...',
        failed: 'Failed to load details.',
        info: 'Basic Info',
        performance: 'Performance & Cost',
        content: 'Request Content',
        responseContent: 'Response Content',
        loadRequestContent: 'Load request content',
        loadResponseContent: 'Load response content',
        loadingPayload: 'Loading content...',
        payloadHint: 'Request and response payloads are loaded on demand to avoid pulling huge text in the detail path.',
        noRequestContent: 'No request content',
        noResponseContent: 'No response content',
        error: 'Error Message',
        requestId: 'Request ID',
        provider: 'Provider',
        type: 'Type',
        stream: 'Stream',
        created: 'Created At',
        retry: 'Retry Count',
        duration: 'Duration'
      }
    },
    settings: {
      title: 'Settings',
      subtitle: 'System configuration and preferences.',
      language: 'Language',
      languageDesc: 'Select system display language.',
      flushNow: 'Flush Now',
      security: 'Client Access Tokens',
      securityDesc: 'Manage tokens used by your applications to authenticate with Lumina.',
      manageTokens: 'Manage Tokens',
      appearance: 'Appearance',
      appearanceDesc: 'Customize the look and feel of the dashboard.',
      light: 'Light',
      dark: 'Dark',
      account: 'Account Settings',
      accountDesc: 'Update administrator username and password. You will need to login again.',
      username: 'Username',
      originalPassword: 'Original Password',
      password: 'New Password',
      confirmPassword: 'Confirm Password',
      leaveBlank: 'Leave blank to keep current',
      updateSuccess: 'Profile updated successfully. Redirecting to login...',
      passwordsDoNotMatch: 'Passwords do not match',
      update: 'Update Profile',
      selfUseMode: {
        title: 'Self-Use Mode',
        desc: 'When enabled, all requests to /v1/** and /v1beta/** will bypass API Key validation. Intended for fully-trusted local or private-network deployments only.',
        warning: 'Security notice: Self-use mode disables API Key authentication. Do not enable on public-facing deployments. Authentication is restored immediately once disabled.',
        activeBadge: 'Active',
        enabled: 'Self-use mode enabled',
        disabled: 'Self-use mode disabled',
      },
      tokens: {
        title: 'API Token Management',
        create: 'Create New Token',
        name: 'Token Name',
        key: 'Secret Key',
        created: 'Created',
        lastUsed: 'Last Used',
        empty: 'No tokens found',
        copied: 'Copied',
        generatedSuccess: 'Token Generated Successfully',
        generateAnother: 'Generate another token',
        activeTokens: 'Active Tokens',
        confirmRevokeShort: 'Confirm Revoke?',
        revokeSuccess: 'Token revoked successfully',
        copyWarning: 'Make sure to copy this token now. You won’t be able to see it again!',
        confirmRevoke: 'Are you sure you want to revoke this token? Any applications using it will lose access immediately.'
      },
      circuitBreaker: {
        title: 'Circuit Breaker Management',
        desc: 'Inspect circuit state, trigger reasons, config source, and operator actions at Provider + Model granularity.',
        refresh: 'Refresh Status',
        updated: 'Circuit breaker updated',
        released: 'Manual control released',
        targetProvider: 'Target Provider',
        loading: 'Loading...',
        empty: 'No circuit-breaker runtime data',
        recentEvents: 'Recent Operator Actions',
        noRecentEvents: 'No manual control events yet',
        filters: {
          provider: 'Provider',
          model: 'Model',
          state: 'State',
          source: 'Config Source',
          manual: 'Control Mode',
          allProviders: 'All Providers',
          allModels: 'All Models',
          allStates: 'All States',
          allSources: 'All Sources',
          allModes: 'All Modes',
          manualOnly: 'Manual Only',
          autoOnly: 'Automatic Only'
        },
        table: {
          provider: 'Provider',
          model: 'Model',
          state: 'State',
          since: 'State Since',
          nextProbe: 'Next Probe',
          score: 'Score',
          requests: 'Requests',
          concurrency: 'Concurrency',
          source: 'Config Source',
          stats: 'Stats (Error/Slow)',
          control: 'Control'
        },
        details: {
          explanation: 'State Explanation',
          failureType: 'Last Failure Type',
          groups: 'Linked Groups',
          thresholds: 'Active Thresholds',
          manualReason: 'Manual Reason',
          noReason: 'None',
          mixed: 'Mixed config context'
        },
        states: {
          CLOSED: 'Normal (CLOSED)',
          OPEN: 'Open (OPEN)',
          HALF_OPEN: 'Probe (HALF_OPEN)'
        },
        sources: {
          global: 'Global',
          group: 'Group',
          provider: 'Provider',
          mixed: 'Mixed'
        },
        controlModal: {
          title: 'Manual Circuit Control',
          targetState: 'Target State',
          reason: 'Reason',
          reasonPlaceholder: 'Enter reason for operation (for audit)',
          duration: 'Duration (ms)',
          confirm: 'Confirm',
          manualActive: 'Manually Controlled',
          currentState: 'Current State',
          currentScore: 'Current Score',
          currentLoad: 'Current Load',
          warningOpen: 'This will force the circuit OPEN and keep the provider unavailable for the specified duration.',
          warningHalfOpen: 'This will force the circuit into HALF_OPEN and only allow probe traffic through.',
          warningClosed: 'This will force the circuit CLOSED and clear the current probe/open counters.',
          releaseHint: 'This instance is already under manual control. Releasing control first is recommended.'
        },
        actions: {
          manage: 'Manage',
          release: 'Release',
          details: 'Details'
        },
        events: {
          control: 'Manual Control',
          release: 'Release Control'
        }
      }
    }
  }
};

interface LanguageContextType {
  language: Language;
  setLanguage: (lang: Language) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
}

const LanguageContext = createContext<LanguageContextType | undefined>(undefined);

export const LanguageProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  // Default to Chinese ('zh')
  const [language, setLanguage] = useState<Language>('zh');

  const t = (key: string, params?: Record<string, string | number>) => {
    const keys = key.split('.');
    let value: any = translations[language];
    for (const k of keys) {
      if (value && value[k]) {
        value = value[k];
      } else {
        return key; // Return key if translation not found
      }
    }

    let str = value as string;
    if (params) {
      Object.entries(params).forEach(([key, val]) => {
        str = str.replace(`{{${key}}}`, String(val));
      });
    }
    return str;
  };

  return (
    <LanguageContext.Provider value={{ language, setLanguage, t }}>
      {children}
    </LanguageContext.Provider>
  );
};

export const useLanguage = () => {
  const context = useContext(LanguageContext);
  if (!context) {
    throw new Error('useLanguage must be used within a LanguageProvider');
  }
  return context;
};
