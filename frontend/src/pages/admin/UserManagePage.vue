<template>
  <div id="userManagePage">
    <PageHeader
      eyebrow="管理 · 用户"
      title="用户管理"
      description="查看和管理平台用户；由管理员创建的账号需要设置初始密码。"
    >
      <template #actions>
        <a-button type="primary" @click="createOpen = true">创建用户</a-button>
      </template>
    </PageHeader>
    <!-- 搜索表单 -->
    <a-form class="search-form" layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="用户 ID">
        <a-input v-model:value="userIdText" placeholder="完整用户 ID" allow-clear style="width: 200px" />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="请输入用户名" allow-clear />
      </a-form-item>
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="请输入账号" allow-clear />
      </a-form-item>
      <a-form-item label="角色">
        <a-select v-model:value="searchParams.userRole" placeholder="请选择角色" allow-clear style="width: 120px">
          <a-select-option :value="0">普通用户</a-select-option>
          <a-select-option :value="1">管理员</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit">搜索</a-button>
          <a-button @click="doReset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <a-modal v-model:open="createOpen" title="创建用户" :footer="null" @cancel="clearCreate">
      <a-form :model="createForm" layout="vertical" @finish="createUser">
        <a-form-item label="账号" name="userAccount" :rules="[{ required: true, min: 4, message: '账号至少4位' }]">
          <a-input v-model:value="createForm.userAccount" autocomplete="off" />
        </a-form-item>
        <a-form-item label="初始密码" name="userPassword" :rules="[{ required: true, min: 6, message: '请填写至少6位的初始密码' }]">
          <a-input-password v-model:value="createForm.userPassword" autocomplete="new-password" />
          <p>6 至 72 个英文字符（约 24 个汉字）。</p>
        </a-form-item>
        <a-form-item label="昵称"><a-input v-model:value="createForm.userName" /></a-form-item>
        <a-button type="primary" html-type="submit" :loading="creating">创建</a-button>
      </a-form>
    </a-modal>

    <a-modal v-model:open="passwordOpen" title="临时密码" :footer="null" :mask-closable="false" @cancel="clearTemporaryPassword">
      <p>只显示这一次。请通过可信方式交给用户，用临时密码登录后，到设置里修改密码。</p>
      <p>该用户已有的登录会话已全部失效。</p>
      <a-input :value="temporaryPassword" readonly aria-label="临时密码" autocomplete="off" />
      <a-space style="margin-top: 16px">
        <a-button type="primary" @click="copyTemporaryPassword">复制临时密码</a-button>
        <a-button @click="clearTemporaryPassword">已保存，关闭</a-button>
      </a-space>
    </a-modal>

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="dataList"
      :loading="loading"
      :pagination="pagination"
      row-key="id"
      @change="handleTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'userAvatar'">
          <a-avatar v-if="record.userAvatar" :src="record.userAvatar" />
          <a-avatar v-else>{{ record.userName?.slice(-1) }}</a-avatar>
        </template>
        <template v-else-if="column.key === 'userRole'">
          <a-tag :color="record.userRole === 1 ? '#1d1d1f' : undefined">
            {{ record.userRole === 1 ? '管理员' : '普通用户' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <!-- 不允许管理员在这里删除自己的账号 -->
          <span v-if="sameId(record.id, loginUserStore.loginUser.id)" class="self-note">当前账号</span>
          <template v-else>
            <a-button type="text" @click="changeRole(record)">{{ record.userRole === 1 ? '设为普通用户' : '设为管理员' }}</a-button>
            <a-button type="text" danger @click="doDelete(record)">删除</a-button>
          </template>
          <a-button type="text" :disabled="resetting" @click="resetPassword(record)">重置密码</a-button>
        </template>
        <template v-else-if="column.key === 'createTime' || column.key === 'updateTime'">
          {{ formatTime(record[column.dataIndex]) }}
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, onBeforeUnmount, reactive, computed } from 'vue';
import { message, Modal } from 'ant-design-vue';
import type { TablePaginationConfig } from 'ant-design-vue';
import { listUserVoByPageUsingPost, deleteUserUsingPost, addUserUsingPost, updateUserUsingPost, resetUserPasswordUsingPost } from '@/service/api/userController';
import PageHeader from '@/components/PageHeader.vue';
import { useLoginUserStore } from '@/stores/useLoginUserStore';
import { parseId, sameId } from '@/utils/space';
import dayjs from 'dayjs';

const createOpen = ref(false);
const creating = ref(false);
const createForm = reactive<API.UserAddRequest>({ userAccount: '', userPassword: '', userName: '' });
function clearCreate() { createForm.userAccount = ''; createForm.userPassword = ''; createForm.userName = ''; }
async function createUser() {
  if (creating.value) return;
  if (!createForm.userPassword || createForm.userPassword.length < 6 || new TextEncoder().encode(createForm.userPassword).length > 72) {
    message.error('密码至少6位，UTF-8编码不超过72字节'); return;
  }
  creating.value = true;
  try {
    const res = await addUserUsingPost({ ...createForm });
    if (res.code !== 0) throw new Error(res.message || '创建失败');
    clearCreate(); createOpen.value = false; message.success('创建成功'); await fetchUserList();
  } catch (e) { message.error(e instanceof Error ? e.message : '创建失败'); }
  finally { creating.value = false; }
}

const passwordOpen = ref(false), resetting = ref(false), temporaryPassword = ref('');
function clearTemporaryPassword() { temporaryPassword.value = ''; passwordOpen.value = false; }
onBeforeUnmount(clearTemporaryPassword);
async function copyTemporaryPassword() {
  if (!temporaryPassword.value) return;
  try { await navigator.clipboard.writeText(temporaryPassword.value); message.success('已复制'); }
  catch { message.error('复制失败，请手动复制'); }
}
function changeRole(record: API.AdminUserVO) {
  if (sameId(record.id, loginUserStore.loginUser.id)) return;
  const role = record.userRole === 1 ? 0 : 1;
  Modal.confirm({ title: role === 1 ? '设为管理员' : '设为普通用户', content: '确认修改此用户的平台角色？',
    async onOk() {
      try {
        const res = await updateUserUsingPost({ id: record.id, userRole: role });
        if (res.code !== 0) throw new Error(res.message || '修改失败');
        message.success('角色已更新'); await fetchUserList();
      } catch (e) { message.error(e instanceof Error ? e.message : '修改失败'); }
    },
  });
}
function resetPassword(record: API.AdminUserVO) {
  if (resetting.value) return;
  Modal.confirm({ title: '重置密码', content: '将生成新的临时密码，并使该用户所有登录会话失效。确认继续？',
    async onOk() {
      if (resetting.value) return;
      resetting.value = true; clearTemporaryPassword();
      try {
        const res = await resetUserPasswordUsingPost({ id: record.id });
        if (res.code !== 0 || !res.data?.temporaryPassword) throw new Error(res.message || '重置失败');
        temporaryPassword.value = res.data.temporaryPassword; passwordOpen.value = true;
      } catch { message.error('重置未确认成功，请勿重复点击；可重新登录后检查用户状态'); }
      finally { resetting.value = false; }
    },
  });
}

// 格式化时间显示
const formatTime = (time: string) => {
  if (!time) return '-';
  return dayjs(time).format('YYYY-MM-DD HH:mm');
};

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id',
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
    key: 'userAvatar',
  },
  {
    title: '用户名',
    dataIndex: 'userName',
    key: 'userName',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
    key: 'userAccount',
  },
  {
    title: '角色',
    dataIndex: 'userRole',
    key: 'userRole',
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
    key: 'userProfile',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
  },
  {
    title: '更新时间',
    dataIndex: 'updateTime',
    key: 'updateTime',
  },
  {
    title: '操作',
    key: 'action',
  },
];

const dataList = ref<API.AdminUserVO[]>([]);
const loading = ref(false);
const total = ref(0);

const loginUserStore = useLoginUserStore();
const userIdText = ref('');

// 搜索条件（统一管理查询参数）
const searchParams = reactive<API.UserQueryRequest>({
  current: 1,
  pageSize: 10,
  // 可扩展的查询条件
  userName: undefined,
  userAccount: undefined,
  userRole: undefined,
});

// 表格分页配置（从 searchParams 读取）
const pagination = computed(() => ({
  current: searchParams.current,
  pageSize: searchParams.pageSize,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50'],
  showTotal: (total: number) => `共 ${total} 条`,
}));

const fetchUserList = async () => {
  const idText = userIdText.value.trim();
  const id = parseId(idText);
  if (idText && !id) {
    message.warning('请输入有效的完整用户 ID');
    return;
  }
  loading.value = true;
  try {
    // 用户 ID 超出 JS 安全整数范围，按字符串提交，由后端解析为 Long
    const res = await listUserVoByPageUsingPost({
      ...searchParams,
      id: id as unknown as number | undefined,
    });
    if (res.code === 0 && res.data) {
      dataList.value = res.data.records || [];
      total.value = Number(res.data.total || 0);
    } else {
      message.error('获取用户列表失败：' + res.message);
    }
  } catch {
    message.error('获取用户列表失败');
  } finally {
    loading.value = false;
  }
};

const handleTableChange = (pag: TablePaginationConfig) => {
  const oldPageSize = searchParams.pageSize || 10;
  const newPageSize = pag.pageSize || oldPageSize;
  searchParams.pageSize = newPageSize;
  searchParams.current = oldPageSize !== newPageSize ? 1 : pag.current || 1;
  fetchUserList();
};

// 搜索
const doSearch = () => {
  searchParams.current = 1; // 搜索时重置到第一页
  fetchUserList();
};

// 重置
const doReset = () => {
  searchParams.current = 1;
  userIdText.value = '';
  searchParams.userName = undefined;
  searchParams.userAccount = undefined;
  searchParams.userRole = undefined;
  fetchUserList();
};

// 删除用户
const doDelete = (record: API.AdminUserVO) => {
  if (sameId(record.id, loginUserStore.loginUser.id)) return;
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除用户「${record.userName || record.userAccount}」吗？`,
    okText: '确定',
    cancelText: '取消',
    okType: 'danger',
    async onOk() {
      try {
        const res = await deleteUserUsingPost({ id: record.id });
        if (res.code === 0) {
          message.success('删除成功');
          fetchUserList();
        } else {
          message.error('删除失败：' + res.message);
        }
      } catch {
        message.error('删除失败');
      }
    },
  });
};

onMounted(() => {
  fetchUserList();
});
</script>

<style scoped>
.search-form {
  gap: 12px 0;
  margin-bottom: 16px;
}
.self-note {
  color: var(--muted);
  font-size: 12px;
}
</style>
