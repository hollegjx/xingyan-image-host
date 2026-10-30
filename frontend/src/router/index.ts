import { createRouter, createWebHistory } from 'vue-router'
import { scrollBehavior } from './scroll'
import { installGalleryNavigation } from '@/utils/galleryNavigation'
import { brandName, pageTitles } from '@/utils/brand'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  scrollBehavior,
  routes: [
    {
      path: '/user/favorites', name: 'my-favorites',
      component: () => import('../pages/user/MyFavoritesPage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/space/invite', name: 'team-invite',
      component: () => import('../pages/space/TeamInvitePage.vue'),
    },
    {
      path: '/user/profile',
      name: 'user-profile',
      component: () => import('../pages/user/UserProfilePage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/user/settings',
      name: 'user-settings',
      component: () => import('../pages/user/UserSettingsPage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/picture/:id/collaborate',
      name: 'collaborate-picture',
      component: () => import('../pages/CollaboratePicturePage.vue'),
      props: true,
      meta: { requiresLogin: true },
    },
    {
      path: '/space/teams',
      name: 'team-spaces',
      component: () => import('../pages/space/TeamSpacesPage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/space/:id/members',
      name: 'space-members',
      component: () => import('../pages/space/SpaceMembersPage.vue'),
      props: true,
      meta: { requiresLogin: true },
    },
    {
      path: '/admin/spaceManage',
      name: 'admin-spaces',
      component: () => import('../pages/admin/SpaceManagePage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/space/:id/analyze',
      name: 'space-analyze',
      component: () => import('../pages/space/SpaceAnalyzePage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/admin/spaceAnalyze',
      name: 'admin-space-analyze',
      component: () => import('../pages/space/SpaceAnalyzePage.vue'),
      props: { adminView: true },
      meta: { requiresLogin: true },
    },
    {
      path: '/space/mine',
      name: 'my-space',
      component: () => import('../pages/space/MySpacePage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/space/:id',
      name: 'space-detail',
      component: () => import('../pages/space/SpaceDetailPage.vue'),
      meta: { requiresLogin: true },
      props: true,
    },
    {
      path: '/',
      name: 'home',
      component: () => import('../pages/HomePage.vue'),
    },
    {
      path: '/user/login',
      name: 'login',
      component: () => import('../pages/user/UserLoginPage.vue'),
    },
    {
      path: '/user/register',
      name: 'register',
      component: () => import('../pages/user/UserRegisterPage.vue'),
    },
    {
      path: '/admin/userManage',
      name: 'admin-users',
      component: () => import('../pages/admin/UserManagePage.vue'),
    },
    {
      path: '/admin/pictureManage',
      name: 'admin-pictures',
      component: () => import('../pages/admin/PictureManagePage.vue'),
    },
    {
      path: '/picture/add',
      name: 'add-picture',
      component: () => import('../pages/AddPicturePage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/picture/mine',
      name: 'my-pictures',
      component: () => import('../pages/MyPicturePage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/picture/search',
      name: 'search-picture',
      component: () => import('../pages/SearchPicturePage.vue'),
      meta: { requiresLogin: true },
    },
    {
      path: '/picture/:id',
      name: 'picture-detail',
      component: () => import('../pages/PictureDetailPage.vue'),
      props: true,
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../pages/NotFoundPage.vue'),
    },
  ],
})

installGalleryNavigation(router)
router.afterEach((to, _from, failure) => {
  if (!failure) document.title = `${pageTitles[String(to.name)] || '图片托管'} · ${brandName}`
})

export default router
