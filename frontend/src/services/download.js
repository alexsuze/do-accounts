import api from './api'

/**
 * 下載後端產生的檔案（需要帶通行證，所以不能直接用 <a href>）：以 blob 取回後觸發瀏覽器下載。
 * @param path API 路徑，例如 '/export/transactions'
 * @param fileName 存檔名稱
 */
export async function download (path, fileName, params = {}) {
  const res = await api.get(path, { params, responseType: 'blob', timeout: 60000 })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = fileName
  document.body.appendChild(a)
  a.click()
  a.remove()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
