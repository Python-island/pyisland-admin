# GET /api/v1/service-status

:::tip 用途
用于查询单个接口的运行状态。
:::

:::warning 公开接口
该接口无需登录，可直接用于监控展示。
:::

:::info 推荐场景
前端可在关键按钮前检测目标接口状态，优化用户提示。
:::

## 基础信息

- Method: `GET`
- Path: `/api/v1/service-status?apiName={apiName}`
- Auth: 否

## 成功响应

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

## 失败响应

```json
{
  "code": 404,
  "message": "接口不存在"
}
```
