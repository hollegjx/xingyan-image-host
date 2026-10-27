import { onBeforeUnmount, onMounted, type ComputedRef } from 'vue'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { Modal, message } from 'ant-design-vue'

export function useUnsavedChanges(dirty: ComputedRef<boolean>, busy: ComputedRef<boolean>) {
  let dialog: ReturnType<typeof Modal.confirm> | undefined
  let pending: Promise<boolean> | undefined
  const guard = () => {
    if (busy.value) {
      message.warning('请等待当前操作完成')
      return false
    }
    if (!dirty.value) return true
    if (pending) return pending
    pending = new Promise<boolean>((resolve) => {
      const finish = (leave: boolean) => {
        pending = undefined
        dialog = undefined
        resolve(leave)
      }
      dialog = Modal.confirm({
        title: '还有未保存的修改',
        content: '离开后，本次修改的名称、简介、分类和标签不会保存。已上传的图片不会被删除。',
        okText: '放弃修改并离开',
        cancelText: '继续编辑',
        autoFocusButton: 'cancel',
        onOk: () => finish(true),
        onCancel: () => finish(false),
      })
    })
    return pending
  }
  function beforeUnload(event: BeforeUnloadEvent) {
    if (dirty.value || busy.value) {
      event.preventDefault()
      event.returnValue = ''
    }
  }
  onBeforeRouteLeave(guard)
  onBeforeRouteUpdate(guard)
  onMounted(() => window.addEventListener('beforeunload', beforeUnload))
  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', beforeUnload)
    dialog?.destroy()
  })
}
