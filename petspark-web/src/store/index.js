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
    user: savedUser
  },
  getters: {
    isAuthenticated: (state) => Boolean(state.accessToken)
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
    clearSession(state) {
      state.accessToken = ''
      state.user = null
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
    logout({ commit }) {
      commit('clearSession')
    }
  },
  modules: {}
})
