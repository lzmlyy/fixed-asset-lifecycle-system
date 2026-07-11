# 盘点扫码功能设计

## 概述

在现有盘点管理模块中增加扫码盘点能力，支持二维码扫描和拍照 OCR 识别资产编号，自动完成单个资产的盘点确认，大幅提升盘点效率。

## 用户场景

1. 在盘点执行页点击"扫码盘点"按钮，弹出扫码对话框
2. 默认扫码模式：摄像头对准资产二维码，自动识别并匹配盘点记录
3. OCR 模式：资产只有手写资产编号（白纸），可拍照上传，后端 OCR 识别文字
4. 识别到资产编号后，支持两种处理模式（由开关控制）
5. 资产台账页可生成/打印资产二维码标签

## 架构

```
前端 ScanDialog.vue
  ├── 二维码模式：html5-qrcode 库，浏览器实时扫码
  ├── OCR 模式：拍照 → 上传图片 → 后端 OCR → 返回文字
  └── 双模式开关：扫码即确认 / 扫码定位高亮

后端
  ├── POST /api/inventory/ocr          接收图片，Tess4J 识别，返回文本行
  ├── GET  /api/inventory/records/lookup?assetCode=xxx&taskId=xxx  按资产编号查找记录
  └── GET  /api/assets/{id}/qrcode     生成资产二维码图片（用于打印标签）

新增依赖
  ├── 前端：html5-qrcode（H5 扫码）、qrcode（生成二维码）
  └── 后端：net.sourceforge.tess4j:tess4j（Tesseract Java 封装）
```

Tesseract 本地安装：需要预装 Tesseract OCR 引擎（Windows: `tesseract.exe`，配置到环境变量或 `application.yml` 指定路径），中文语言包 `chi_sim`。

---

## 详细设计

### 1. ScanDialog.vue — 扫码弹窗组件

#### 状态变量
| 变量 | 类型 | 说明 |
|------|------|------|
| `mode` | `'qr' \| 'ocr'` | 当前模式（Tab 切换） |
| `quickConfirm` | `boolean` | 扫码即确认开关（默认 true） |
| `scanResults` | `ScanResult[]` | 最近 5 条扫描结果 |
| `ocrImage` | `string \| null` | OCR 模式下拍照/上传的图片 data URL |
| `ocrText` | `string` | OCR 识别出的文本 |
| `ocrLoading` | `boolean` | OCR 识别中 |
| `manualAssetCode` | `string` | OCR 识别不准确时手动修正的资产编号 |

#### ScanResult 类型
```typescript
interface ScanResult {
  assetCode: string
  assetName: string
  recordId: number | null   // 匹配到的盘点记录 ID，null 表示未在盘点范围内
  result: 'matched' | 'already_scanned' | 'not_found'
  scannedAt: Date
}
```

#### 模板结构
```
<el-dialog fullscreen>
  <el-tabs v-model="mode">
    <el-tab-pane label="扫码">         二维码扫码区域   </el-tab-pane>
    <el-tab-pane label="拍照 OCR">     拍照/上传区域     </el-tab-pane>
  </el-tabs>
  <底部>
    <el-switch v-model="quickConfirm">  快速确认开关  </el-switch>
    <最近扫描结果列表（最多 5 条）>
  </底部>
</el-dialog>
```

#### 扫码模式交互
1. 使用 `html5-qrcode` 启动后置摄像头
2. 画面中央有扫描框动画（CSS border + pulse 动画）
3. 扫到二维码 → 解析 assetCode → 调用 lookup API
4. 匹配成功：
   - `quickConfirm=true` → 自动调用 `scanInventoryRecord`，绿色 toast，加入结果列表
   - `quickConfirm=false` → 绿色 toast + 在父组件盘点列表中高亮该行并滚动到视图
5. 已扫过：黄色 toast "该资产已盘点"
6. 未匹配：红色 toast "未在盘点范围内"

#### OCR 模式交互
1. 点击"拍照"打开摄像头拍照 / 点击"上传"选择本地图片
2. 图片显示在对话框左侧预览区
3. 自动调用 `/api/inventory/ocr` 上传识别
4. 识别结果展示在右侧文本框，可手动编辑修正
5. 点击"查找资产"按钮 → 调用 lookup API → 同上匹配逻辑
6. 查找不到时，手动修改 assetCode 重新查找

---

### 2. 后端 OCR 接口

#### POST /api/inventory/ocr
- 接收 `multipart/form-data`，字段 `image`
- 将图片保存为临时文件
- 调用 Tess4J API：`Tesseract.doOCR(imageFile)`
- 删除临时文件
- 返回 `{ text: "识别出的所有文字行" }`
- 前端从返回文本中用正则提取资产编号（如 `ZC\d+` 或用户输入的格式）

#### Tesseract 初始化
```java
@Configuration
public class TesseractConfig {
    @Value("${tesseract.datapath}")
    private String datapath;

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(datapath);
        tesseract.setLanguage("chi_sim+eng");
        return tesseract;
    }
}
```

`application.yml` 新增：
```yaml
tesseract:
  datapath: ${TESSDATA_PREFIX:C:/Program Files/Tesseract-OCR/tessdata}
```

---

### 3. 查找盘点记录接口

#### GET /api/inventory/records/lookup
- 参数：`assetCode` (String, required)、`taskId` (Long, required)
- 逻辑：在 inventory_record 表中 JOIN asset 表，按 asset_code 匹配，且 record.task_id = taskId
- 返回：
```json
{
  "recordId": 1,
  "assetCode": "ZC20240001",
  "assetName": "笔记本电脑",
  "expectedLocation": "3楼A区",
  "expectedKeeper": "张三",
  "result": "PENDING",
  "scanned": false
}
```
- `scanned=true` 表示已盘点（result 不为 PENDING）

---

### 4. 二维码生成（资产标签打印）

#### GET /api/assets/{id}/qrcode
- 返回 PNG 图片（`image/png`），内容为资产的 `asset_code`
- 使用 ZXing 库（已随 Tess4J 引入）生成二维码
- 前端在资产台账页的资产详情弹窗或操作列增加"二维码"按钮，点击展示可打印的二维码标签

#### 前端资产标签展示
- 在 AssetDetail.vue 或 AssetList.vue 增加"生成二维码"按钮
- 点击后弹出小型对话框：展示二维码图片（大尺寸） + 资产编号文字 + 资产名称文字
- 底部有"打印"按钮，调用 `window.print()` 或单独的打印 CSS
- 每页可排 6-8 个标签（A4 纸），支持批量选择资产后批量打印

---

### 5. InventoryExecute.vue 改动

- 头部操作栏增加"扫码盘点"按钮（主色调，icon 用相机图标）
- 引入 `<ScanDialog>` 组件
- 表格行增加 `ref` 用于高亮定位
- 接收 `scan-locate` 事件，接收 recordId 后滚动到该行并高亮闪烁 3 秒

---

### 6. 依赖清单

| 依赖 | 版本 | 用途 |
|------|------|------|
| html5-qrcode | latest | 前端 H5 摄像头扫码 |
| qrcode | latest | 前端生成二维码图片 |
| net.sourceforge.tess4j:tess4j | 5.11.0 | Java OCR 引擎封装 |
| com.google.zxing:core | 3.5.3 | 后端生成二维码（Tess4J 传递依赖已包含） |
| Tesseract OCR | 5.x | 系统级安装（Windows: 需单独安装） |

---

### 7. 错误处理

| 场景 | 处理 |
|------|------|
| 摄像头权限被拒绝 | toast 提示"请允许摄像头权限" + 切换到 OCR 模式建议 |
| 二维码内容非资产编号格式 | toast 提示"未识别的二维码格式" |
| OCR 识别结果为空 | 提示"未能识别出文字，请重新拍摄清晰照片或手动输入" |
| 资产编号匹配不到盘点记录 | 红色 toast"未在盘点范围内"，结果列表显示 not_found |
| 资产已被盘点 | 黄色 toast"该资产已盘点"，不做重复操作 |
| Tesseract DLL 未安装 | 后端启动时 warn 日志 + OCR 接口返回 503 |
| OCR 图片过大 (>10MB) | 前端压缩到 1920px 宽度后再上传 |

---

### 8. 不做什么

- 不做移动端独立 App — 用响应式 Web 适配
- 不做条形码支持 — 二维码和 OCR 已覆盖场景
- 不做离线盘点 — 依赖后端 API
- 不做 RFID 扫描 — YAGNI
