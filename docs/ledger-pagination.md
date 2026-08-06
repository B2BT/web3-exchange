# 资金明细分页契约（Phase 1.3）

> 作者：PM · exchange-asset 域
> 范围：①资金流水(ledger)分页对外接口 ②前端资产页"资金明细"分页表格
> 说明：当前为单现货账户（无现货↔理财/合约多账户），"资产划转"暂缓（无划转目标账户）；本次聚焦流水完整视图。

## 一、后端接口（exchange-asset）

### `GET /api/asset/ledger/list`
请求：`?userId=<id>&page=1&size=20`（page 默认1, size 默认20, 上限100）
响应：`Result<PageData<LedgerVO>>`
```json
{
  "code": 200,
  "data": {
    "total": 156,
    "current": 1,
    "size": 20,
    "pages": 8,
    "records": [
      { "id": "...", "requestId": "…", "userId": "...", "accountId": "...", "symbol": "USDT",
        "bizType": "DEPOSIT", "direction": 1, "amount": 1000000000,
        "beforeAvailable": 0, "afterAvailable": 1000000000,
        "beforeFrozen": 0, "afterFrozen": 0, "refNo": "…", "status": 1, "remark": "充值",
        "createTime": "2026-08-06 10:00:00" }
    ]
  }
}
```
- 实现：按 userId 查账户（AccountService，可能有多个币种账户）→ 对该用户的流水按 `create_time DESC` 分页。
- 复用现有 `LedgerService.pageLedgers(accountId, page, size)`（已有）——需要 userId→accounts 映射 + 跨账户聚合（或按账户分页后合并）。最简单：查该 userId 所有账户 id，按 createTime 倒序 union 分页（可用 MyBatis-Plus `SELECT * FROM t_ledger WHERE user_id=#{userId} ORDER BY create_time DESC LIMIT offset,size`）。
- `LedgerVO` 已含全部字段；雪花 Long id 序列化为 String（@JsonSerialize ToStringSerializer，仅 id 类字段，金额保留 number）。

## 二、前端（Asset.vue 新增"资金明细"tab）
- 资产页现有 tabs：资产总览 / 充值 / 提现。新增 **资金明细** tab。
- 分页表格（el-table + el-pagination）：
  - 列：时间 / 币种(symbol) / 业务类型(bizType 中文映射) / 方向(入/出/冻结/解冻/冻结转出) / 金额(正,带符号色) / 变动后可用余额
  - bizType 映射：FREEZE=冻结 UNFREEZE=解冻 TRANSFER_IN=转入 TRANSFER_OUT=转出 DEPOSIT=充值 WITHDRAW=提现 FEE=手续费 REBATE=返佣
  - direction 映射：1=入 2=出 3=冻结 4=解冻 5=冻结转出
  - 金额展示用现有 formatLong（金额精度按 symbol 的 coinDecimals）+ 方向着色（入/冻结=红或绿按币圈习惯；统一：入=绿,出=红,冻结=橙）。
- 切换 tab 加载第 1 页；翻页/改 size 重新拉取；加载态/空态。
- 新增 api/asset.ts：`ledgerList(params:{userId,page,size})` → GET /asset/ledger/list。

## 三、验收
- 接口按 userId 分页返回流水（total/records 正确），createTime 降序。
- 前端资金明细 tab 分页表格正确显示，翻页生效；金额精度正确。
- 不破坏现有 资产总览/充值/提现 tab 与充值提现流程。
- `mvn -pl exchange-asset -am compile` BUILD SUCCESS；`vue-tsc` 0 错误 + build 通过。
