# GET /api/v1/service-status/list

:::tip 用途
用于批量获取所有接口状态，适合状态看板页面。
:::

:::warning 公开接口
该接口无需登录。
:::

:::info 建议
可搭配定时轮询实现服务健康状态实时展示。
:::

## 基础信息

- Method: `GET`
- Path: `/api/v1/service-status/list`
- Auth: 否

## 成功响应

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
