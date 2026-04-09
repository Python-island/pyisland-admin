# 服务状态接口

> 路径前缀：`/api/v1/service-status`

## 查询单个接口状态

- **Method**: `GET`
- **Path**: `/api/v1/service-status?apiName={apiName}`
- **Auth**: 否

### 响应

成功：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "apiName": "version",
    "status": true,
    "message": "运行正常",
    "remark": "",
    "updatedAt": "2026-04-09T12:00:00"
  }
}
```

不存在：

```json
{
  "code": 404,
  "message": "接口不存在"
}
```

## 查询全部接口状态

- **Method**: `GET`
- **Path**: `/api/v1/service-status/list`
- **Auth**: 否

### 成功响应

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": 1,
      "apiName": "version",
      "status": true,
      "message": "运行正常",
      "remark": "",
      "updatedAt": "2026-04-09T12:00:00"
    }
  ]
}
```

## 更新接口状态

- **Method**: `PUT`
- **Path**: `/api/v1/service-status`
- **Auth**: 是
- **Content-Type**: `application/json`

### 请求体

```json
{
  "apiName": "version",
  "status": true,
  "message": "运行正常",
  "remark": "可用"
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `apiName` | `string` | 是 | 接口名称 |
| `status` | `boolean` | 是 | 状态开关 |
| `message` | `string` | 否 | 状态消息 |
| `remark` | `string` | 否 | 备注 |

### 响应

成功：

```json
{
  "code": 200,
  "message": "接口状态更新成功",
  "data": {
    "apiName": "version",
    "status": true,
    "message": "运行正常",
    "remark": "可用",
    "updatedAt": "2026-04-09T12:00:00"
  }
}
```

参数错误：

```json
{
  "code": 400,
  "message": "接口名称不能为空"
}
```

```json
{
  "code": 400,
  "message": "接口状态不能为空"
}
```

## 前端调用示例

### fetch

```ts
const baseUrl = "https://server.pyisland.com/api";

export async function getServiceStatusList() {
  const res = await fetch(`${baseUrl}/v1/service-status/list`);
  const json = await res.json();
  if (json.code !== 200) throw new Error(json.message);
  return json.data;
}

export async function updateServiceStatus(payload: {
  apiName: string;
  status: boolean;
  message?: string;
  remark?: string;
}) {
  const token = localStorage.getItem("token") || "";
  const res = await fetch(`${baseUrl}/v1/service-status`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify(payload),
  });
  return res.json();
}
```

### axios

```ts
import axios from "axios";

const http = axios.create({
  baseURL: "https://server.pyisland.com/api",
});

export async function getServiceStatus(apiName: string) {
  const { data } = await http.get("/v1/service-status", {
    params: { apiName },
  });
  if (data.code !== 200) throw new Error(data.message);
  return data.data;
}
```
