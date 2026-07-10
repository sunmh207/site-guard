/// Site 表单校验的单元测试
///
/// 使用 valibot 的 v.safeParse 验证 schema 行为。
/// 覆盖：合法路径、空名称、名称超长、URL 缺协议、URL 含协议。
import { describe, expect, it } from 'vitest'
import * as v from 'valibot'
import { siteCreateSchema } from './site.schema'

describe('siteCreateSchema', () => {
  it('accepts valid input', () => {
    const result = v.safeParse(siteCreateSchema, {
      name: '官网',
      url: 'https://example.com',
    })
    expect(result.success).toBe(true)
  })

  it('rejects empty name', () => {
    const result = v.safeParse(siteCreateSchema, {
      name: '',
      url: 'https://example.com',
    })
    expect(result.success).toBe(false)
  })

  it('rejects name over 128 chars', () => {
    const result = v.safeParse(siteCreateSchema, {
      name: 'a'.repeat(129),
      url: 'https://example.com',
    })
    expect(result.success).toBe(false)
  })

  it('rejects url without protocol', () => {
    const result = v.safeParse(siteCreateSchema, {
      name: '官网',
      url: 'example.com',
    })
    expect(result.success).toBe(false)
  })

  it('rejects bare-path url', () => {
    const result = v.safeParse(siteCreateSchema, {
      name: '官网',
      url: '/just/a/path',
    })
    expect(result.success).toBe(false)
  })

  it('accepts http://', () => {
    const result = v.safeParse(siteCreateSchema, {
      name: '官网',
      url: 'http://example.com',
    })
    expect(result.success).toBe(true)
  })
})