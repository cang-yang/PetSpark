import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex)

const storage = window.localStorage

function readJson(key) {
  if (!storage) {
    return null
  }
  try {
    const value = storage.getItem(key)
    return value ? JSON.parse(value) : null
  } catch (err) {
    storage.removeItem(key)
    return null
  }
}

const savedToken = storage ? storage.getItem('petspark.accessToken') : ''
const savedUser = readJson('petspark.user')

export default new Vuex.Store({
  state: {
    accessToken: savedToken || '',
    user: savedUser,
    notificationUnreadCount: 0
  },
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken),
    notificationUnreadCount: (state) => state.notificationUnreadCount
  },
  mutations: {
    setSession(state, session) {
      state.accessToken = session.accessToken
      state.user = session.user
      if (storage) {
        storage.setItem('petspark.accessToken', session.accessToken)
        storage.setItem('petspark.user', JSON.stringify(session.user))
      }
    },
    setUser(state, user) {
      state.user = user
      if (storage) {
        storage.setItem('petspark.user', JSON.stringify(user))
      }
    },
    setNotificationUnreadCount(state, count) {
      const value = Number(count)
      state.notificationUnreadCount = Number.isFinite(value) && value > 0 ? value : 0
    },
    clearSession(state) {
      state.accessToken = ''
      state.user = null
      state.notificationUnreadCount = 0
      if (storage) {
        storage.removeItem('petspark.accessToken')
        storage.removeItem('petspark.user')
      }
    }
  },
  actions: {
    saveLogin({ commit }, loginResponse) {
      commit('setSession', {
        accessToken: loginResponse.accessToken,
        user: loginResponse.user
      })
    },
    async refreshNotificationUnreadCount({ commit, getters }) {
      if (!getters.isAuthenticated) {
        commit('setNotificationUnreadCount', 0)
        return 0
      }
      const { getUnreadNotificationCount } = await import('@/api/notifications')
      const res = await getUnreadNotificationCount()
      const count = res && res.data ? res.data.unreadCount : 0
      commit('setNotificationUnreadCount', count)
      return count
    },
    setNotificationUnreadCount({ commit }, count) {
      commit('setNotificationUnreadCount', count)
    },
    logout({ commit }) {
      commit('clearSession')
    }
  },
  modules: {}
})
