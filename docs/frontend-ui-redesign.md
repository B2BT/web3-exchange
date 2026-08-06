# 前端 UI 改版：Web3 科技感主题

> 状态：实施中 · 作者：项目经理（PM）· 基于主流交易平台调研
> 调研对象：Binance（深黑画布+黄色 CTA）、OKX/Bybit（midnight 深色）、Web3 设计趋势（玻璃拟态 + 霓虹渐变 + 发光）

## 目标
把当前**浅色单调**的界面（白 header + #f5f7fa 浅灰底）整体改造成**深色 Web3 科技感**：深黑画布、玻璃拟态卡片、霓虹渐变（紫→青）强调、发光动效、等宽数字字体。

## 设计 Tokens（前端全局 CSS 变量，写在 `src/styles/theme.css`）
```css
:root {
  --bg-base: #0a0e17;              /* 画布底色（近黑深蓝） */
  --bg-elevated: #111827;          /* 次级背景（卡片/面板） */
  --glass-bg: rgba(17, 24, 39, 0.55);      /* 玻璃卡片底 */
  --glass-bg-strong: rgba(17, 24, 39, 0.75);
  --glass-border: rgba(255, 255, 255, 0.08); /* 玻璃描边 */
  --glass-hover-border: rgba(125, 211, 252, 0.35);

  --accent-grad: linear-gradient(135deg, #7c3aed 0%, #06b6d4 100%); /* 主渐变 紫→青 */
  --accent-purple: #a78bfa;
  --accent-cyan: #22d3ee;
  --accent-blue: #3b82f6;

  --up: #22c55e;     /* 涨（绿） */
  --down: #ef4444;   /* 跌（红） */
  --warning: #f59e0b;

  --text-primary: #e5e7eb;
  --text-secondary: #9ca3af;
  --text-dim: #64748b;

  --font-sans: 'Inter', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  --font-num: 'SF Mono', 'Roboto Mono', 'JetBrains Mono', ui-monospace, monospace;

  --glow-purple: 0 0 24px rgba(124, 58, 237, 0.35);
  --glow-cyan: 0 0 24px rgba(34, 211, 238, 0.3);
  --radius: 14px;
  --radius-sm: 8px;
}
```

## 视觉规范
### 1. 全局背景
- 画布 `--bg-base`，叠加**两层装饰**（App.vue 或 MainLayout 的 fixed 层）：
  - 网格线：`background-image: linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px), linear-gradient(90deg, ...)` 25px 间距；
  - 顶部霓虹光晕：`radial-gradient(600px 300px at 20% -10%, rgba(124,58,237,0.18), transparent)` 和 `radial-gradient(700px 350px at 85% -5%, rgba(34,211,238,0.12), transparent)`。

### 2. 顶栏（MainLayout）
- 玻璃拟态：`background: rgba(10,14,23,0.6); backdrop-filter: blur(12px); border-bottom: 1px solid var(--glass-border)`。
- Logo「Web3 交易所」用渐变文字 + 发光：`background: var(--accent-grad); -webkit-background-clip: text; color: transparent;` + `filter: drop-shadow`。
- 菜单：active 项底部渐变指示条（`::after` 3px 渐变条），文字用亮色；hover 文字提亮。
- 用户区：头像加渐变描边圆环。

### 3. 卡片（glass card 通用类 `.g-card`）
```css
.g-card {
  background: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius);
  backdrop-filter: blur(10px);
  transition: border-color .25s, box-shadow .25s, transform .25s;
}
.g-card:hover { border-color: var(--glass-hover-border); box-shadow: var(--glow-cyan); }
```
ticker 卡片、行情卡片、交易面板、资产卡片、订单表格容器、通知列表、用户中心卡片**统一套 `.g-card`**。

### 4. 按钮
- 主按钮：`background: var(--accent-grad); color:#fff; box-shadow: var(--glow-purple);` hover 提亮+位移 1px。
- 危险/卖出按钮：红色渐变；买入按钮：绿色渐变（交易页 Buy/Sell 区分）。

### 5. K 线图（KlineChart.vue）
- 深色主题：背景透明、网格线 `rgba(255,255,255,0.06)`、坐标轴文字 `#64748b`。
- 蜡烛：涨 `var(--up)` 跌 `var(--down)`，影线同色。
- MA：MA5 `#f59e0b`、MA10 `#22d3ee`、MA20 `#a78bfa`（霓虹亮色）。
- 成交量柱：涨绿跌红（半透明）。
- 图例/十字线 tooltip：深色底 + 发光。

### 6. 数字字体
- 所有价格/数量/金额/涨跌值用 `font-family: var(--font-num); font-variant-numeric: tabular-nums;`（等宽对齐）。
- ticker、K线 y 轴、交易金额、资产余额、订单金额统一应用。

### 7. 动效
- 卡片 hover 上浮 `translateY(-2px)` + 发光边框；
- 页面切换：router-view 外层 `transition: opacity .2s, transform .2s`（fade+轻微上移）；
- 登录页卡片入场：轻微缩放淡入 `@keyframes fadeInUp`。

## 各页面改造清单
| 页面 | 改造点 |
|------|--------|
| `App.vue` | 引入 theme.css；`--bg-base` 背景 + 网格/光晕装饰层 |
| `MainLayout.vue` | 顶栏玻璃拟态 + 渐变 Logo + 菜单 active 渐变条 + 头像渐变环 |
| `Login.vue` / `Register.vue` | 深色背景 + 玻璃登录卡片 + 渐变标题 + 发光主按钮 |
| `Market.vue` | ticker 卡片改玻璃卡 + 渐变涨跌色 + 数字等宽 |
| `KlineChart.vue` | 霓虹 K 线配色 + 暗色网格 + 图例发光 |
| `Trade.vue` | 买卖面板玻璃卡 + Buy 绿/Sell 红渐变按钮 + 数字等宽 |
| `Asset.vue` | 资产总览玻璃卡 + 余额等宽数字 + 充值/提现卡 |
| `Order.vue` | 表格容器玻璃卡 + 金额等宽 |
| `Notify.vue` | 列表卡片玻璃化 + 未读徽标霓虹 |
| `UserCenter.vue` | 资料卡玻璃化 + 头像渐变环 |
| `Placeholder.vue` | 空态文案样式适配深色 |

## Element Plus 深色适配
- 组件覆盖（`theme.css` 或 `styles/element-override.css`）：
  - `--el-bg-color` / `--el-bg-color-overlay` → 深色玻璃值；
  - `--el-text-color-primary` → `var(--text-primary)`；`--el-border-color` → `var(--glass-border)`；
  - 或直接给 Element Plus 套 `dark` class + 自定义 CSS 变量，保证 el-table/el-card/el-input/el-menu 跟随深色。
- 表格：表头 `--bg-elevated`，行 hover 高亮，斑马纹关闭或深色半透明。

## 验收标准
- 登录页 → 各页面均为深色主题，无明显浅色残留；
- ticker / K 线 / 金额等数字等宽对齐、可读；
- K 线蜡烛 + MA + 成交量在深色下霓虹醒目；
- 玻璃卡片 hover 有发光反馈；
- `vue-tsc` 源码 0 错误，`npm run build` 通过；
- 不破坏登录 E2E（深色下验证码仍可见、登录可提交）。
