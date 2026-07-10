type MessageType = 'info' | 'warning' | 'success' | 'error'

interface MessageOptions {
  /** 消息的唯一标识符，用于更新或移除特定消息 */
  id?: string
  /** 消息标题 */
  title: string
  /** 消息描述信息 */
  description?: string
  /** 消息显示时长（毫秒），默认为 3000ms */
  duration?: number
}

interface MessageUpdateOptions {
  /** 消息标题 */
  title?: string
  /** 消息描述信息 */
  description?: string
  /** 消息显示时长（毫秒），默认为 3000ms */
  duration?: number
}

/**
 * 消息工具类
 * 基于 useToast 封装，提供 info、warning、success、error 四种消息类型
 */
export const useMessage = () => {
  const toast = useToast()

  /**
   * 发送信息消息
   */
  const info = (options: MessageOptions | string) => {
    const opts = typeof options === 'string' ? { title: options } : options
    toast.add({
      id: opts.id,
      title: opts.title,
      description: opts.description,
      color: 'info',
      icon: 'i-lucide-circle-alert',
      duration: opts.duration,
      close: true,
    })
  }

  /**
   * 发送警告消息
   */
  const warning = (options: MessageOptions | string) => {
    const opts = typeof options === 'string' ? { title: options } : options
    toast.add({
      id: opts.id,
      title: opts.title,
      description: opts.description,
      color: 'warning',
      icon: 'i-lucide-circle-alert',
      duration: opts.duration,
      close: true,
    })
  }

  /**
   * 发送成功消息
   */
  const success = (options: MessageOptions | string) => {
    const opts = typeof options === 'string' ? { title: options } : options
    toast.add({
      id: opts.id,
      title: opts.title,
      description: opts.description,
      color: 'success',
      icon: 'i-lucide-circle-check',
      duration: opts.duration,
      close: true,
    })
  }

  /**
   * 发送错误消息
   */
  const error = (options: MessageOptions | string) => {
    const opts = typeof options === 'string' ? { title: options } : options
    toast.add({
      id: opts.id,
      title: opts.title,
      description: opts.description,
      color: 'error',
      icon: 'i-lucide-circle-alert',
      duration: opts.duration,
    })
  }

  /**
   * 根据类型发送消息
   * @param type 消息类型：info、warning、success、error
   * @param options 消息选项或消息标题字符串
   */
  const open = (type: MessageType, options: MessageOptions | string) => {
    switch (type) {
      case 'info':
        info(options)
        break
      case 'warning':
        warning(options)
        break
      case 'success':
        success(options)
        break
      case 'error':
        error(options)
        break
    }
  }

  /**
   * 更新指定的消息
   * @param id 消息的唯一标识符
   * @param options 更新选项
   */
  const update = (id: string, options: MessageUpdateOptions) => {
    toast.update(id, {
      title: options.title,
      description: options.description,
      duration: options.duration,
    })
  }

  /**
   * 移除指定的消息
   * @param id 消息的唯一标识符
   */
  const remove = (id: string) => {
    toast.remove(id)
  }

  /**
   * 清除所有消息
   */
  const clear = () => {
    toast.clear()
  }

  return {
    open,
    info,
    warning,
    success,
    error,
    update,
    remove,
    clear,
  }
}
