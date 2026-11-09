import { defineStore } from 'pinia'
import api from '../services/api'
import { clearSession, loadSession, saveSession } from '../services/session'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: loadSession().token || null,
    user: loadSession().user || null
  }),
  getters: {
    loggedIn: (s) => !!s.token,
    isAdmin: (s) => !!s.user?.admin
  },
  actions: {
    /** credential：Google Identity Services 回傳的 ID token */
    async login (credential) {
      const { data } = await api.post('/auth/google', { credential })
      this.token = data.token
      this.user = data.user
      saveSession(data.token, data.user)
    },
    /** 重新取得目前使用者（名稱、管理員身分可能被改過） */
    async refresh () {
      const { data } = await api.get('/auth/me', { silent: true })
      // 以瀏覽器裡的通行證為準：store 可能在登入前就建立（例如另一個分頁登入），不能用 null 蓋掉
      this.token = loadSession().token || this.token
      this.user = data
      if (this.token) saveSession(this.token, data)
    },
    /** 記帳偏好（本位幣、每週起始日、預算警示門檻），只送有變的欄位 */
    async updateSettings (body) {
      const { data } = await api.put('/auth/me/settings', body)
      this.user = data
      saveSession(this.token, data)
      return data
    },
    logout () {
      this.token = null
      this.user = null
      clearSession()
      window.google?.accounts?.id?.disableAutoSelect?.()
    }
  }
})
