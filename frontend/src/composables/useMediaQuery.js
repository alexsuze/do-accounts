import { onBeforeUnmount, ref } from 'vue'

/** 媒體查詢是否成立（例如判斷桌面寬度，切換表格／卡片） */
export function useMediaQuery (query) {
  const mql = window.matchMedia(query)
  const matches = ref(mql.matches)
  const onChange = (e) => { matches.value = e.matches }
  mql.addEventListener('change', onChange)
  onBeforeUnmount(() => mql.removeEventListener('change', onChange))
  return matches
}
