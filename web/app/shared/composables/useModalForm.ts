/**
 * 表单模态框的通用逻辑
 * @param defaultState 表单的默认状态
 * @returns 返回模态框的开关状态、表单状态、重置函数等
 */
export function useModalForm<T extends Record<string, any>>(defaultState: T) {
  // 控制模态框开关
  const open = ref(false)

  // 表单状态
  const state = reactive<T>({ ...defaultState } as T)

  // 重置表单函数
  function resetForm() {
    Object.assign(state, defaultState)
  }

  // 监听对话框关闭，清空表单
  watch(open, (newValue: boolean) => {
    if (!newValue) {
      resetForm()
    }
  })

  return {
    open,
    state,
    resetForm,
  }
}
