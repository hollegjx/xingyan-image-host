import { generateService } from '@umijs/openapi'

await generateService({
  requestLibPath: "import request from '@/request'",
  schemaPath: process.env.OPENAPI_SCHEMA_PATH || 'http://localhost:8123/api/v2/api-docs',
  serversPath: './src/service',
  // 后端 Long 以字符串返回。允许安全的小整数请求，但不能把雪花 ID 强转 Number。
  hook: {
    // Swagger 2 的 basePath 转换后可能只留在 servers；统一生成项目的 /api 相对路径。
    afterOpenApiDataInited(schema) {
      schema.paths = Object.fromEntries(
        Object.entries(schema.paths).map(([path, value]) => [
          '/api' + path.replace(/^\/(?:tuku\/)?api(?=\/)/, ''),
          value,
        ]),
      )
      return schema
    },
    customType(schema) {
      if (schema?.format === 'int64') return 'string | number'
      if (schema?.type === 'array' && schema.items?.format === 'int64')
        return '(string | number)[]'
    },
  },
})
