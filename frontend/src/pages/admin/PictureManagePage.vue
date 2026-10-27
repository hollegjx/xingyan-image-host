<template>
  <div id="pictureManagePage">
    <div class="page-actions">
      <a-button type="primary" @click="openBatchUploadModal">批量导入</a-button>
    </div>

    <!-- 搜索表单 -->
    <a-form class="search-form" layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词">
        <a-input v-model:value="searchParams.searchText" placeholder="搜索名称或简介" allow-clear />
      </a-form-item>
      <a-form-item label="分类">
        <a-input v-model:value="searchParams.category" placeholder="请输入分类" allow-clear />
      </a-form-item>
      <a-form-item label="标签">
        <a-select
          v-model:value="searchParams.tags"
          mode="multiple"
          placeholder="请选择标签"
          :options="tagOptions"
          allow-clear
          style="min-width: 200px"
        />
      </a-form-item>
      <a-form-item label="审核状态">
        <a-select
          v-model:value="searchParams.reviewStatus"
          placeholder="请选择审核状态"
          :options="reviewStatusOptions"
          style="min-width: 140px"
        />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit">搜索</a-button>
          <a-button @click="doReset">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>

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
        <template v-if="column.key === 'url'">
          <PictureThumbnail :picture="record" width="80px" :height="60" preview />
        </template>
        <template v-else-if="column.key === 'tags'">
          <a-tag v-for="tag in record.tags" :key="tag" color="blue">{{ tag }}</a-tag>
        </template>
        <template v-else-if="column.key === 'picSize'">
          {{ formatSize(record.picSize) }}
        </template>
        <template v-else-if="column.key === 'user'">
          {{ record.user?.userName || '-' }}
        </template>
        <template v-else-if="column.key === 'reviewInfo'">
          <div class="review-info">
            <div>审核状态：{{ getReviewStatusText(record.reviewStatus) }}</div>
            <div>审核信息：{{ record.reviewMessage || '-' }}</div>
            <div>审核人：{{ record.reviewerId || '-' }}</div>
            <div>审核时间：{{ formatTime(record.reviewTime) }}</div>
          </div>
        </template>
        <template v-else-if="column.key === 'createTime'">
          {{ formatTime(record.createTime) }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button
              v-if="canReviewAs(record, REVIEW_STATUS.PASS)"
              type="link"
              @click="doReview(record, REVIEW_STATUS.PASS)"
            >
              通过
            </a-button>
            <a-button
              v-if="canReviewAs(record, REVIEW_STATUS.REJECT)"
              type="link"
              danger
              @click="doReview(record, REVIEW_STATUS.REJECT)"
            >
              拒绝
            </a-button>
            <a-button type="link" @click="doEdit(record)">编辑</a-button>
            <a-button type="link" danger @click="doDelete(record)">删除</a-button>
          </a-space>
        </template>
      </template>
    </a-table>

    <PictureBatchUploadModal
      v-model:open="batchUploadModalOpen"
      :category-options="categoryOptions"
      :tag-options="tagOptions"
      @success="handleBatchUploadSuccess"
    />
    <PictureReviewModal
      v-model:open="reviewModalOpen"
      :picture="reviewTarget"
      :review-status="targetReviewStatus"
      :confirm-loading="reviewSubmitting"
      @submit="submitReview"
    />
  </div>
</template>

<script lang="ts" setup>
import { ref, onMounted, reactive, computed } from 'vue';
import { message, Modal } from 'ant-design-vue';
import PictureThumbnail from '@/components/PictureThumbnail.vue';
import type { TablePaginationConfig } from 'ant-design-vue';
import {
  listPictureReviewByPageUsingPost,
  deletePictureUsingPost,
  listPictureTagCategoryUsingGet,
  reviewPictureUsingPost,
} from '@/service/api/pictureController';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import {
  PICTURE_REVIEW_STATUS as REVIEW_STATUS,
  PICTURE_REVIEW_STATUS_OPTIONS,
  PICTURE_REVIEW_STATUS_TEXT,
} from '@/constants/picture';
import PictureBatchUploadModal from '@/components/admin/PictureBatchUploadModal.vue';
import PictureReviewModal from '@/components/admin/PictureReviewModal.vue';

const router = useRouter();

// 格式化时间
const formatTime = (time?: string) => {
  if (!time) return '-';
  return dayjs(time).format('YYYY-MM-DD HH:mm');
};

// 格式化文件大小
const formatSize = (size: number) => {
  if (!size) return '-';
  if (size < 1024) return size + ' B';
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB';
  return (size / 1024 / 1024).toFixed(1) + ' MB';
};

const reviewStatusOptions = PICTURE_REVIEW_STATUS_OPTIONS;

const getReviewStatusText = (status?: number) => {
  if (status === undefined || status === null) return '-';
  return PICTURE_REVIEW_STATUS_TEXT[status] || '未知状态';
};

const columns = [
  {
    title: 'ID',
    dataIndex: 'id',
    key: 'id',
    width: 80,
  },
  {
    title: '图片',
    dataIndex: 'url',
    key: 'url',
    width: 100,
  },
  {
    title: '名称',
    dataIndex: 'name',
    key: 'name',
    width: 150,
  },
  {
    title: '分类',
    dataIndex: 'category',
    key: 'category',
    width: 100,
  },
  {
    title: '标签',
    dataIndex: 'tags',
    key: 'tags',
    width: 200,
  },
  {
    title: '大小',
    dataIndex: 'picSize',
    key: 'picSize',
    width: 100,
  },
  {
    title: '尺寸',
    dataIndex: 'picWidth',
    key: 'picWidth',
    width: 120,
    customRender: ({ record }: { record: API.PictureVO }) => {
      return `${record.picWidth} x ${record.picHeight}`;
    },
  },
  {
    title: '上传者',
    dataIndex: 'user',
    key: 'user',
    width: 100,
  },
  {
    title: '审核信息',
    key: 'reviewInfo',
    width: 180,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
    key: 'createTime',
    width: 150,
  },
  {
    title: '操作',
    key: 'action',
    width: 220,
    fixed: 'right',
  },
];

const dataList = ref<API.PictureVO[]>([]);
const loading = ref(false);
const total = ref(0);
const categoryOptions = ref<{ value: string }[]>([]);
const tagOptions = ref<{ value: string }[]>([]);
const batchUploadModalOpen = ref(false);
const reviewModalOpen = ref(false);
const reviewSubmitting = ref(false);
const reviewTarget = ref<API.PictureVO>();
const targetReviewStatus = ref<number>(REVIEW_STATUS.PASS);

// 搜索条件
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  searchText: undefined,
  category: undefined,
  tags: undefined,
  reviewStatus: REVIEW_STATUS.REVIEWING,
});

// 表格分页配置
const pagination = computed(() => ({
  current: searchParams.current,
  pageSize: searchParams.pageSize,
  total: total.value,
  showSizeChanger: true,
  pageSizeOptions: ['10', '20', '50', '100'],
  showTotal: (total: number) => `共 ${total} 条`,
}));

const fetchPictureList = async () => {
  loading.value = true;
  try {
    const res = await listPictureReviewByPageUsingPost(searchParams);
    if (res.code === 0 && res.data) {
      dataList.value = res.data.records || [];
      total.value = Number(res.data.total || 0);
    } else {
      message.error('获取图片列表失败：' + res.message);
    }
  } catch {
    message.error('获取图片列表失败');
  } finally {
    loading.value = false;
  }
};

const handleTableChange = (pag: TablePaginationConfig) => {
  searchParams.current = pag.current;
  searchParams.pageSize = pag.pageSize;
  fetchPictureList();
};

// 搜索
const doSearch = () => {
  searchParams.current = 1;
  fetchPictureList();
};

// 重置
const doReset = () => {
  searchParams.current = 1;
  searchParams.searchText = undefined;
  searchParams.category = undefined;
  searchParams.tags = undefined;
  searchParams.reviewStatus = REVIEW_STATUS.REVIEWING;
  fetchPictureList();
};

// 编辑
const doEdit = (record: API.PictureVO) => {
  router.push({
    path: '/picture/add',
    query: { id: record.id },
  });
};

// 打开审核弹窗
const doReview = (record: API.PictureVO, reviewStatus: number) => {
  if (!record.id) {
    message.error('图片 ID 不能为空');
    return;
  }
  reviewTarget.value = record;
  targetReviewStatus.value = reviewStatus;
  reviewModalOpen.value = true;
};

const submitReview = async (reviewMessage: string) => {
  if (reviewSubmitting.value || !reviewTarget.value?.id) return;
  reviewSubmitting.value = true;
  try {
    const res = await reviewPictureUsingPost({
      id: reviewTarget.value.id,
      reviewStatus: targetReviewStatus.value,
      reviewMessage: reviewMessage || undefined,
    });
    if (res.code === 0) {
      message.success('审核成功');
      reviewModalOpen.value = false;
      reviewTarget.value = undefined;
      fetchPictureList();
    } else {
      message.error('审核失败：' + res.message);
    }
  } catch {
    message.error('审核失败');
  } finally {
    reviewSubmitting.value = false;
  }
};

const canReviewAs = (record: API.PictureVO, reviewStatus: number) => {
  return record.reviewStatus !== reviewStatus;
};

const openBatchUploadModal = () => {
  batchUploadModalOpen.value = true;
};

const handleBatchUploadSuccess = () => {
  searchParams.current = 1;
  searchParams.reviewStatus = REVIEW_STATUS.PASS;
  fetchPictureList();
};

// 删除
const doDelete = (record: API.PictureVO) => {
  Modal.confirm({
    title: '确认删除',
    content: `确定要删除图片「${record.name || record.id}」吗？`,
    okText: '确定',
    cancelText: '取消',
    okType: 'danger',
    async onOk() {
      try {
        const res = await deletePictureUsingPost({ id: record.id });
        if (res.code === 0) {
          message.success('删除成功');
          fetchPictureList();
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
  fetchPictureList();
  fetchTagOptions();
});

// 获取分类和标签选项
const fetchTagOptions = async () => {
  try {
    const res = await listPictureTagCategoryUsingGet();
    if (res.code === 0 && res.data) {
      categoryOptions.value = (res.data.categoryList || []).map((category) => ({ value: category }));
      tagOptions.value = (res.data.tagList || []).map((tag) => ({ value: tag }));
    }
  } catch (error) {
    console.error('获取标签列表失败', error);
  }
};
</script>

<style scoped>
#pictureManagePage {
  padding: 16px;
}

.page-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16px;
}

.search-form {
  margin-bottom: 16px;
}

.review-info {
  line-height: 1.8;
}
</style>
