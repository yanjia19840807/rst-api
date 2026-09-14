# RST Sizing / Slot 图表计算规则

口径对齐 Excel Demo 4.0（Workbook §11.2）。实现以当前代码为准。

| 图 | 所属模拟 | 数据是否共用 |
|---|---|---|
| Monthly Volume vs Overtime | Sizing（月） | 月 sizing 行 + 月 Volume + Team Setup |
| Daily Volume vs Backlog Aging - Full Period | Sizing（日） | 日 sizing 行 + Team Setup TAT |
| Monthly SLA% vs Goal | Sizing（日汇总） | **同一套**日 sizing 行和 aging，不读月图 |
| Per-Shift FTE Available vs Theoretical FTE Needed | Slot | **不读** Sizing 结果；只用 Per-slot Volume / 班次 / Team Setup / Cycle Time |

Sizing 管「编制够不够（月/日）」；Slot 管「班次在一天里盖不盖得住」。界面上需先跑完 Sizing 才能点 Slot，这是流程门闩，不是公式输入。

两个都叫 SLA 的数不要混：

| 名字 | Team Setup 字段 | 作用 |
|---|---|---|
| SLA Turntime（TAT，时限） | `slaTurnaroundMinutes` + `workingHoursPerDay` | 允许多久做完；日图虚线、超龄判定、Slot FIFO |
| SLA Goal（目标达成率） | `slaTargetRatio` | 只要达到百分之多少；只画虚线，不进 SLA% / 日 TAT 公式 |

```
goalDays = slaTurnaroundMinutes / 60 / workingHoursPerDay
```

例：480 分钟、每天 8 小时 → `goalDays = 1`。

---

## 1. Monthly Volume vs Overtime

### 用途

在当前 **Right Sizing HC** 下，看每个月业务量够不够、要不要加班、会不会产能过剩。左轴 FTE（加班柱 + HC 线），右轴 Volume。

时间窗：

- 历史月：`sizingMonth − 2`、`− 1`、`sizingMonth`
- 预测月：月度 forecast 点（一般随后约 3 个月）

### 输入

| 来源 | 字段 | 用途 |
|---|---|---|
| Exercise | `sizingMonth` | 历史/预测分界 |
| 月 Volume | `actualVolume`、`commercialRatio` | 历史量；Commercial 进 Manual |
| 月 Forecast | `acceptedValue` / `forecastMean` | 预测月量 |
| Team Setup | `workingHoursPerDay`、`availabilityRatio`、`capacityRatio`、`automationRatio`、`maxOvertimeMinutes`、`weekendShiftHc`、`weekendCode` | 产能与加班上限 |
| Cycle Time | active baseline `medianSeconds` | 单件秒数 |
| Scenario | `rightSizingHc` | 模拟编制 |
| Holiday | 休息日 | 计入 WeekendDays |
| 日历 | WorkDays / WeekendDays | `WeekendDays = 当月天数 − WorkDays` |

### 中间变量（后端 `sizeMonthlyRow` / `SizingMath`）

对每个月：

```
Volume        = 历史月 actual；预测月 accepted / mean
Manual        = Volume × (1 − Automation) × (1 + Commercial)
Max HC        = Nominal HC without OT
              = Manual × CycleTime / 3600 / (WorkDays × WorkingHr × Avail × Cap)
Min HC        = Nominal HC with OT
              = Manual × CycleTime / 3600 / (WorkDays × (WorkingHr + MaxOT分钟/60) × Avail × Cap)
                − (WeekendDays / WorkDays) × WeekendShiftHc
              （结果 < 0 则取 0）
Right Size HC = rightSizingHc（每月相同）
```

`forecastVolume` 字段在历史月写入的也是 actual，只是字段名没改。

### 中间变量（前端 `monthlyOtFte`，不落库）

```
stdProd     = RS_HC × WorkDays × WorkingHr × Avail × Cap × 3600 / CycleTime
weekendProd = WeekendShiftHc × WeekendDays × WorkingHr × Avail × Cap × 3600 / CycleTime
produced    = stdProd + weekendProd
gapFte      = (Manual − produced) × CycleTime / 3600 / (WorkDays × WorkingHr × Avail × Cap)

Weekdays Overtime = produced > Manual ? 0 : gapFte          // 人手不够，正数
Overcapacity      = produced < Manual ? 0 : gapFte          // 人手有余，为负（柱在 0 轴下）
Max Overtime      = RS_HC × MaxOT分钟 × Cap / 60 / (WorkingHr × Avail)
                    // 制度加班上限折成 FTE，不看当月量
```

RS_HC / CycleTime / 分母为 0 时，三根柱都为 0。没有 sizing 行或没有 Team Setup 时，柱和 HC 线都不画。

### 输出（图上系列）

| 系列 | 怎么来 | 轴 |
|---|---|---|
| Volume（深色线） | 有月 actual 用 actual；否则用 sizing `forecastVolume` | Volume |
| Volume Forecasted（浅色线） | 无 actual 且有 sizing 行 → `forecastVolume` | Volume |
| Overcapacity | `monthlyOtFte.overcapacity` | FTE |
| Max Overtime | `monthlyOtFte.maxOvertime` | FTE |
| Weekdays Overtime | `monthlyOtFte.weekdaysOvertime` | FTE |
| Max HC (No OT) | `nominalHcWithoutOt` | FTE |
| Right Size HC | `rightSizingHc` | FTE |
| Min HC (Full OT) | `nominalHcWithOt` | FTE |

预测月 Volume 与 Volume Forecasted 会画同一组点，深色在上层，浅色基本被盖住。历史月改 Volume 后若不重跑 Sizing，线会变、柱仍用旧 `manualVolume`。

### 图表含义

- 绿柱（Weekdays OT）往上：这个月要加班
- 红柱（Overcapacity）往下：产能闲着（当前实现是负的 gapFte）
- 蓝柱（Max OT）：加班天花板；绿柱高过它 = 加班需求顶破上限
- 三条线：编制（RS）夹在「不加班需求」和「打满加班 + 周末班需求」之间
- 深/浅量线：历史实际 vs 预测

代码：`SizingSimulationService.sizeMonthlyRow`、`SizingMath`、`sizingChartMath.monthlyOtFte`、`SizingSimulationCharts.monthlyOption`。

---

## 2. Daily Volume vs Backlog Aging - Full Period

### 用途

按天滚 backlog，看积压「老」了几天、哪几天破 TAT。右轴柱是每天量，左轴线是 aging（天）。

**Full Period**（闭区间，一天不漏）：

| | 规则 |
|---|---|
| 起点 | 日 Volume 最早一条 actual；没有则用 sizing 年 1 月 1 日 |
| 终点 | `sizingMonth + 1` 个月最后一天 |

例：sizing = 2026-06、最早 actual = 2026-01-15 → **2026-01-15 ~ 2026-07-31**。第一天 `backlogStart = 0`，没有期初积压输入。

### 输入

| 来源 | 字段 | 用途 |
|---|---|---|
| 日 Volume | `actualVolume`、`dailyAdjustmentRatio` | 有 actual 用 actual；Daily Adj 进 Manual |
| 日 Forecast | `acceptedValue` / `forecastMean` | 无 actual 时的量；再没有为 0 |
| 月 Volume | `commercialRatio`（按月） | 进每日 Manual |
| Team Setup | Automation、工时、Avail、Cap、Max OT、Weekend Shift、Skeleton、周末码 | 日类型产能 |
| Cycle Time | `medianSeconds` | 产能件数 |
| Scenario | `rightSizingHc` | 工作日在岗 HC |
| Holiday | Holiday / Weekend / Normal | 日类型 |
| Team Setup TAT | `slaTurnaroundMinutes`、`workingHoursPerDay` | 前端虚线与 OK/KO |

空日（窗口内既无 actual 也无 forecast）Volume = 0，行仍在，周末也滚，aging 才连续。

日图**不再读** Daily Volume 表画柱；柱上的数就是 sizing 行的 `forecastVolume`（历史天后端写入的是当天 actual）。

### 中间变量（后端 `computeDailyView`，逐日）

**① 日类型**（`WorkingDaysCalculator.volumeDay`）

| 日历 | holiday | workingDay | 在岗 HC |
|---|---|---|---|
| 普通工作日 | false | true | Right Sizing HC |
| 普通周末 | false | false | Weekend Shift HC |
| 假期表 Holiday / Weekend | true | false | `floor(RS_HC × Skeleton)` |
| 调休 Normal | false | true | Right Sizing HC |

假日优先于周末。周六标成 Holiday 走 Skeleton，不走 Weekend Shift。

**② Volume / Manual**

```
Volume = 有日 actual ? actual : (forecast 或 0)
Manual = Volume × (1 − Auto) × (1 + Commercial) × (1 + DailyAdj)
```

**③ 产能（件数，ROUNDDOWN）**

```
Standard = floor(SimHc × WorkingHr × 3600 × Avail × Cap / CycleTime)
OT       = 仅工作日且 MaxOT > 0：
           floor(SimHc × MaxOT分钟 × 60 × Avail × Cap / CycleTime)
         否则 0
```

周末有 Weekend Shift 的 Standard，OT 一定为 0。假日有 Skeleton 的 Standard，OT 为 0。

**④ Backlog**

```
BacklogEnd = max(0, BacklogStart + Manual − Standard − OT)
次日 Start = 当日 End
```

落库字段：`forecastVolume`、`manualVolume`、`holiday`、`workingDay`、`simulationHc`、`standardCapacity`、`overtimeCapacity`、`backlogStart`、`backlogEnd`。**没有 aging**。

### 中间变量（前端）

```
goalDays = slaTurnaroundMinutes / 60 / workingHoursPerDay   // 虚线高度
```

`backlogAgingDays`（Excel 列 AB）：

```
BacklogEnd = 0          → aging = 0
周末或假日               → aging = 昨天 aging（冻结）
BacklogStart < 当天产能 → aging = 1
否则                    → aging = min(昨天+1, ceil(BacklogStart / 当天产能))
```

OK / KO：`aging > goalDays` 为 KO。KO 点绿、红都给同一个 aging，绿线不断、红线叠在超标段上（画法，不是两套 aging）。

Volume 柱着色：`月份 ≤ sizingMonth` → 深色 Volume；之后 → 浅色 Volume Forecasted。

### 输出（图上系列）

| 系列 | 含义 |
|---|---|
| Volume | 历史段每天量（字段仍叫 forecastVolume） |
| Volume Forecasted | 预测月每天量 |
| SLA Turntime | 水平虚线 = `goalDays` |
| Backlog OK | aging（绿） |
| Backlog KO | aging 超 TAT 的点（红） |

没配 TAT 或工时时虚线为 `null`；aging 仍可算。改 TAT / 工时不用重跑 Sizing；改 HC / Volume / Cycle Time 必须重跑。

### 图表含义

- 柱高：当天业务量
- 绿线贴 0：当天清完
- 绿线上爬但仍在虚线下：积压在变老，仍达标
- 变红：积压天数 > TAT
- 周末线横着：不工作，年龄冻结
- 线掉到 0：那天 `BacklogEnd = 0`，不是换月重置

代码：`SizingSimulationService.computeDailyView`、`SizingMath`、`sizingChartMath.backlogAgingDays` / `slaGoalDays`、`SizingSimulationCharts.dailyOption`。

---

## 3. Monthly SLA% vs Goal

### 用途

把日图的 aging 收成「这个月有百分之多少 **Manual 量**还在 TAT 内」，再和 SLA Goal 比。

**整张图在前端算。** 后端没有 Monthly SLA% 字段。提示文案已按 Excel 量加权口径：`1 − 超龄量 / Manual`，不是「达标工作日占比」。

### 输入

| 来源 | 用途 |
|---|---|
| 日 sizing 行（与日图同一份） | Manual、backlog、产能、日类型、日期 |
| `slaTurnaroundMinutes`、`workingHoursPerDay` | `goalDays`；缺失则不跑汇总，图为空轴 |
| `slaTargetRatio` | 虚线 Goal%；缺失则不画虚线，SLA% 照算 |

月份 = Full Period 里出现过的月。起点不是 1 号时，第一个月是**残月**。

### 中间变量

**1. aging**：与日图同一函数 `backlogAgingDays`。

**2. 当天超龄量** `backlogOutOfSlaVolume`（Excel 列 AC）

```
aging ≤ goalDays                    → 0
aging > goalDays 且 期初 < 当天产能 → BacklogEnd
aging > goalDays 且 期初 ≥ 当天产能 → 当天 Manual
```

已超龄且今天能把昨天留下的做完：只把下班还剩的算超龄。连期初都做不完：今天整份 Manual 算超龄。

**3. 按月**

```
Σmanual += Manual
Σout    += 超龄量

Σmanual ≤ 0 → SLA% = 0
否则        → max(0, 1 − Σout / Σmanual) × 100
```

`out > manual` 时卡成 0%，不会出现负数。

**4. Goal 虚线**

```
goal% = round(slaTargetRatio × 1000) / 10
```

`0.95` → `95.0`。只画线，不进 SLA% 公式。

### 输出（图上系列）

| 系列 | 含义 |
|---|---|
| SLA% | 当月量加权达标率（0–100） |
| SLAGoal | Team Setup 目标达成率（水平虚线） |

空态要求有 daily sizing 行。日图 Volume 着色、红绿描边不进本图。月加班柱 / HC 线也不进。

### 图表含义

- 点在虚线上方：这个月达到公司目标
- 点低：超龄 **量** 多（量大的 KO 日比量小的更伤），不是「红点天数」
- 只改 Goal：虚线动、点不动
- 只改 TAT / 工时：日图虚线 + 本图 SLA% 一起变，不用重跑
- 改 HC / Volume / Cycle Time：必须重跑日模拟

代码：`sizingChartMath.monthlySlaPercents` / `backlogOutOfSlaVolume`、`SizingSimulationCharts.slaOption`。

---

## 4. Per-Shift FTE Available vs Theoretical FTE Needed

### 用途

Slot Period 里每个 30 分钟格：班次实际在岗多少人，按量理论该有多少人，当天 TAT 是否还在目标上。

与前三张图**不是一条积压链**，量来自 **Per-slot Volume**。`Calendar ≤ 24h / BH ≤ 8h` 门槛已取消，不再拦跑 Slot。

### 输入

| 来源 | 字段 | 用途 |
|---|---|---|
| Per-slot Volume | `actualVolume`、槽起止时间 | Raw；槽长一般 30 分钟 |
| Scenario 班次 | Start、Duration、Capacity FTE、**该班次 Team Weekend** | 堆叠柱 |
| Team Setup | Automation、Availability、Turntime、`slaTargetRatio` | Manual、产能、FIFO、目标线 |
| Cycle Time | `medianSeconds` | 每 FTE 件数 |

**不用：** Capacity Ratio、Commercial、Daily Adj、假期表、Right Sizing HC、日/月 sizing 行。周末只看**各班次自己的** Team Weekend。

### 中间变量（后端 `SlotSimulationService.compute` / `SlotMath`）

**1. Manual / 理论 FTE**

```
Manual         = Raw × (1 − Automation)
casesPerFte    = 槽分钟 × 60 / CycleTime秒 × Availability
theoreticalFte = Manual / casesPerFte
```

图上再 `ROUND(..., 0)`（Excel chart8）。行里仍是 6 位小数。

**2. 各班次在岗**

- 时间窗 `[start, start+duration)` 是否盖住本槽（可跨午夜）
- 班次日 = Excel `INT(slotDateTime − shiftStart)`（跨夜用班次开始那天判周末）
- 该班次 Team Weekend 含这一天 → 贡献 0

```
贡献     = 盖住且非周末 ? Capacity FTE : 0
shiftFte = Σ 各班次贡献
capacity = shiftFte × casesPerFte
BacklogEnd = max(0, BacklogStart + Manual − capacity)
```

Slot 积压只在 Slot Period 内滚。

**3. FIFO 超龄（给 TAT）**

```
slaSlots = ceil(Turntime分钟 / 槽分钟)   // 至少 1
```

8 小时、30 分钟槽 → 16 格。量按进槽批次排队，产能先消化最早的；待满 `slaSlots` 未做完，整批记入本格 `volumeOutsideSla`。

**4. 后端行上的 TAT / KPI**

```
整段 TAT on period = 1 − Σout / ΣManual     // 从 Period 首槽一直累，不按天重置
Actual vs theoretical = Σcapacity / ΣManual
```

### 中间变量（前端画图，有 rows 时会重算）

**Cumulative Daily TAT**（Excel chart8，与 KPI **不是同一条**）：

```
换日则 cumRaw、cumOut 清零
cumRaw += Raw
cumOut += volumeOutsideSla
TAT = cumRaw ≤ 0 ? 100% : max(0, 1 − cumOut / cumRaw)
```

| | 图上日 TAT | KPI TAT on period |
|---|---|---|
| 窗口 | 当天从 0 累 | 整个 Slot Period |
| 分母 | Raw | Manual |

Instant TAT（`1 − out/manual`）代码里有，**这张图不画**。

Target TAT = `slaTargetRatio`（右轴 0–1）。没配不画。

### 输出（图上系列）

| 系列 | 轴 | 含义 |
|---|---|---|
| Shift N FTE（堆叠柱） | FTE | 该槽各班次在岗人数 |
| Theoretical FTE for Manual Volume | FTE | `ROUND(理论人数)` |
| Target TAT | TAT Ratio | SLA Goal |
| Cumulative Daily TAT | TAT Ratio | 当天累计按时完成比例 |

### 图表含义

- 柱 ≥ 虚线：这格人手盖得住理论需求
- 柱 < 虚线：缺人，积压往后堆，过了 `slaSlots` 会超龄
- 日 TAT 贴 100%：当天还没有超龄量
- 日 TAT 下掉 / 低于 Target：当天已有量超过 Turntime，或没到公司目标
- 换日线会跳回：日 TAT 每天重置
- 某班次周末柱为 0：该班次 Team Weekend，不是公共假期

代码：`SlotSimulationService.compute`、`SlotMath`、`slotChartMath.cumulativeDailyTat` / `roundedTheoreticalFte`、`SlotSimulationCharts.vue`。

---

## 四张图怎么串

```
月 Volume + Forecast + Team Setup + Cycle Time + RS HC
        │
        ├─ 月 sizing 行 ──► 图1 Monthly Volume vs Overtime
        │                   （HC 线后端；加班柱前端 monthlyOtFte）
        │
日 Volume + Forecast + 同上
        │
        └─ 日 sizing 行（逐日 backlog）
                ├─► 图2 Daily Volume vs Backlog Aging
                │     （产能/backlog 后端；aging / OK·KO 前端）
                └─► 图3 Monthly SLA% vs Goal
                      （同一套 aging，按月量加权）

Per-slot Volume + 班次 + Team Setup + Cycle Time
        │
        └─► 图4 Per-Shift FTE vs Theoretical FTE
              （与 1–3 无关；Turntime 只用于 Slot FIFO）
```

改 TAT / 工时：图 2 虚线、图 3 SLA% 一起变。  
改 `slaTargetRatio`：只动图 3 / 图 4 的目标虚线。  
改 RS HC：图 1–3 需重跑 Sizing；图 4 不变。  
改班次：只影响图 4，需重跑 Slot。
