# 行政区划数据维护说明

鹊桥·红娘工作台使用**全量合法行政区划**（省→市→区/县）作为信息收集页的
三级级联数据源，并在后端做"城市限定区县 + 省市匹配"校验，杜绝随意输入脏数据。

本目录下三个脚本构成**数据维护工具链**， annual 维护（如民政部区划调整）时按流程操作即可。

## 数据落点（single source of truth）

| 角色 | 路径 |
|------|------|
| 权威可编辑源 | `source/admin-divisions.json` |
| 发布数据（派生） | `cities.json` |
| 前端主源 HTML | `index.html` （锚点 `const CITIES = __CITIES__;`） |
| 前端副本 HTML | `../queqiao-server-java/src/main/resources/static/collect.html` （同锚点） |
| 后端校验加载源 | `../queqiao-server-java/src/main/resources/cities.json` |
| 后端运行期 classpath | `../queqiao-server-java/target/classes/...`（由 maven 或 sync 同步） |

> **原则**：`source/admin-divisions.json` 是唯一人工维护入口；`cities.json`
> 与两处 HTML、后端 resources 全部由脚本派生，禁止手改派生文件。

## 维护流程

### 1. 修改权威源
编辑 `source/admin-divisions.json`，结构：
```json
{
  "省份名": {
    "地级市名": ["区/县名", "..."],   // 不设区市值为 []
    "...": []
  }
}
```
- 直辖市：省份=市，如 `"北京": {"北京": [...]}`
- 不设区市（东莞/中山/嘉峪关/儋州/三沙等）：区县列表为空 `[]`
- 港澳台按实际区划录入

### 2. 校验并生成发布数据
```bash
python gen_cities.py            # 校验完整性并打印统计，生成 cities.json
python gen_cities.py --check    # 仅校验，不写文件
```
脚本会自检：省份数(≥34)、地级市数(≥340)、区县数(≥2800)、不设区市清单、
省内区县重名（同名不同市属会提示人工确认）。

### 3. 同步到所有落点
```bash
python sync_cities.py                  # 全量同步：2 HTML + 后端 resources + classes
python sync_cities.py --check          # 仅校验四处一致性，不写文件
python sync_cities.py --skip-classes   # 不同步 target/classes（交给 maven）
```
同步后若后端 dev 运行需重新加载 resources，执行：
```bash
mvn -o process-resources
```

### 4. 验证
- 前端：浏览器打开 `index.html`，确认省/市/区三级联动正常、不设区市（如东莞）自动补同名占位项。
- 后端：调用 `POST /public/register` 或 `set` 接口，用错位区县（如"浙江/杭州/桥西区"）应返回 400。

## 注意事项
- **不要手改** `cities.json`、`index.html` 内联 CITIES、`collect.html` 内联 CITIES、
  后端 `resources/cities.json` —— 它们由脚本生成，改动会被下次同步覆盖。
- 两个 HTML 使用统一锚点 `const CITIES = __CITIES__;`。若锚点被人手改回内联 JSON，
  `sync_cities.py` 会拒绝写入并提示恢复锚点，避免数据再次漂移。
- 海南的省直辖县级市（五指山/琼海/文昌等）在前端作为"市"层级、区县为空处理，
  这是登记场景的层级简化，非数据错误。
- 字段口径以民政部最新县级行政区划为准；年度维护时核对调整项即可。
