declare namespace API {
  type PictureDetailVO = PictureVO & { favoriteCount?: number; favorited?: boolean }
  type BaseResponsePictureDetailVO_ = { code?: number; message?: string; data?: PictureDetailVO }

  type AiOutpaintingRequest = {
    externalProcessingConsent?: boolean
    pictureId?: string | number
    requestId?: string
    xScale?: number
    yScale?: number
  }

  type AiTaskVO = {
    createTime?: string
    expiresAt?: string
    id?: string
    message?: string
    pictureId?: string | number
    resultUrl?: string
    status?: string
  }

  type applyUsingPOSTParams = {
    /** taskId */
    taskId: string
  }

  type BaseResponseAiTaskVO_ = {
    code?: number
    data?: AiTaskVO
    message?: string
  }

  type BaseResponseBoolean_ = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseInt_ = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponseListDistribution_ = {
    code?: number
    data?: Distribution[]
    message?: string
  }

  type BaseResponseListImageSearchResult_ = {
    code?: number
    data?: ImageSearchResult[]
    message?: string
  }

  type BaseResponseListPictureVO_ = {
    code?: number
    data?: PictureVO[]
    message?: string
  }

  type BaseResponseListRanking_ = {
    code?: number
    data?: Ranking[]
    message?: string
  }

  type BaseResponseListSpaceLevelVO_ = {
    code?: number
    data?: SpaceLevelVO[]
    message?: string
  }

  type BaseResponseListSpaceUserVO_ = {
    code?: number
    data?: SpaceUserVO[]
    message?: string
  }

  type BaseResponseLoginUserVO_ = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong_ = {
    code?: number
    data?: string | number
    message?: string
  }

  type BaseResponseMapStringBoolean_ = {
    code?: number
    data?: Record<string, any>
    message?: string
  }

  type BaseResponsePagePicture_ = {
    code?: number
    data?: PagePicture_
    message?: string
  }

  type BaseResponsePagePictureVO_ = {
    code?: number
    data?: PagePictureVO_
    message?: string
  }

  type BaseResponsePageSpace_ = {
    code?: number
    data?: PageSpace_
    message?: string
  }

  type BaseResponsePageSpaceUserVO_ = {
    code?: number
    data?: PageSpaceUserVO_
    message?: string
  }

  type BaseResponsePageSpaceVO_ = {
    code?: number
    data?: PageSpaceVO_
    message?: string
  }

  type BaseResponsePageUserVO_ = {
    code?: number
    data?: PageUserVO_
    message?: string
  }

  type BaseResponsePicture_ = {
    code?: number
    data?: Picture
    message?: string
  }

  type BaseResponsePictureProcessResult_ = {
    code?: number
    data?: PictureProcessResult
    message?: string
  }

  type BaseResponsePictureTagCategory_ = {
    code?: number
    data?: PictureTagCategory
    message?: string
  }

  type BaseResponsePictureVO_ = {
    code?: number
    data?: PictureVO
    message?: string
  }

  type BaseResponseSpace_ = {
    code?: number
    data?: Space
    message?: string
  }

  type BaseResponseSpaceAnalysisVO_ = {
    code?: number
    data?: SpaceAnalysisVO
    message?: string
  }

  type BaseResponseSpaceUserVO_ = {
    code?: number
    data?: SpaceUserVO
    message?: string
  }

  type BaseResponseSpaceVO_ = {
    code?: number
    data?: SpaceVO
    message?: string
  }

  type BaseResponseString_ = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseTrend_ = {
    code?: number
    data?: Trend
    message?: string
  }

  type BaseResponseUsage_ = {
    code?: number
    data?: Usage
    message?: string
  }

  type BaseResponseUser_ = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO_ = {
    code?: number
    data?: UserVO
    message?: string
  }

  type Distribution = {
    count?: string | number
    name?: string
    totalSize?: string | number
  }

  type getPictureByIdUsingGETParams = {
    /** id */
    id?: string | number
  }

  type getPictureVOByIdUsingGETParams = {
    /** id */
    id?: string | number
  }

  type getPrivateFileUsingGETParams = {
    /** id */
    id: string | number
    /** variant */
    variant?: string
  }

  type getSpaceUsingGETParams = {
    /** id */
    id: string | number
  }

  type getSpaceVOUsingGETParams = {
    /** id */
    id: string | number
  }

  type getUserByIdUsingGETParams = {
    /** id */
    id?: string | number
  }

  type getUserVOByIdUsingGETParams = {
    /** id */
    id?: string | number
  }

  type getUsingGETParams = {
    /** taskId */
    taskId?: string
  }

  type ImageSearchResult = {
    fromUrl?: string
    thumbUrl?: string
  }

  type latestUsingGETParams = {
    /** pictureId */
    pictureId?: string | number
  }

  type LoginUserVO = {
    createTime?: string
    editTime?: string
    id?: string | number
    updateTime?: string
    userAccount?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: number
  }

  type MapStringBoolean_ = true

  type PagePicture_ = {
    current?: string | number
    pages?: string | number
    records?: Picture[]
    size?: string | number
    total?: string | number
  }

  type PagePictureVO_ = {
    current?: string | number
    pages?: string | number
    records?: PictureVO[]
    size?: string | number
    total?: string | number
  }

  type PageSpace_ = {
    current?: string | number
    pages?: string | number
    records?: Space[]
    size?: string | number
    total?: string | number
  }

  type PageSpaceUserVO_ = {
    current?: string | number
    pages?: string | number
    records?: SpaceUserVO[]
    size?: string | number
    total?: string | number
  }

  type PageSpaceVO_ = {
    current?: string | number
    pages?: string | number
    records?: SpaceVO[]
    size?: string | number
    total?: string | number
  }

  type PageUserVO_ = {
    current?: string | number
    pages?: string | number
    records?: UserVO[]
    size?: string | number
    total?: string | number
  }

  type Picture = {
    category?: string
    createTime?: string
    editTime?: string
    id?: string | number
    introduction?: string
    isDelete?: number
    name?: string
    originalUrl?: string
    picColor?: string
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: string | number
    picWidth?: number
    reviewMessage?: string
    reviewStatus?: number
    reviewTime?: string
    reviewerId?: string | number
    sourceUrl?: string
    spaceId?: string | number
    tags?: string
    thumbnailUrl?: string
    updateTime?: string
    uploadStatus?: number
    url?: string
    userId?: string | number
  }

  type PictureDeleteRequest = {
    id?: string | number
  }

  type PictureEditByBatchRequest = {
    category?: string
    nameRule?: string
    pictureIdList?: (string | number)[]
    spaceId?: string | number
    tags?: string[]
  }

  type PictureEditRequest = {
    category?: string
    id?: string | number
    introduction?: string
    name?: string
    tags?: string[]
  }

  type PictureProcessInfoVO = {
    format?: string
    frameCount?: number
    height?: number
    key?: string
    size?: string | number
    url?: string
    width?: number
  }

  type PictureProcessRequest = {
    outputDir?: string
    pictureId?: string | number
    rules?: PictureProcessRule[]
  }

  type PictureProcessResult = {
    original?: PictureProcessInfoVO
    processedList?: PictureProcessInfoVO[]
  }

  type PictureProcessRule = {
    outputFileName?: string
    rule?: string
  }

  type PictureQueryRequest = {
    category?: string
    current?: number
    endEditTime?: string
    id?: string | number
    introduction?: string
    name?: string
    pageSize?: number
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: string | number
    picWidth?: number
    reviewStatus?: number
    searchText?: string
    sortField?: string
    sortOrder?: string
    spaceId?: string | number
    startEditTime?: string
    tags?: string[]
    uploadStatus?: number
    userId?: string | number
  }

  type PictureReviewRequest = {
    id?: string | number
    reviewMessage?: string
    reviewStatus?: number
  }

  type PictureTagCategory = {
    categoryList?: string[]
    tagList?: string[]
  }

  type PictureUpdateRequest = {
    category?: string
    id?: string | number
    introduction?: string
    name?: string
    tags?: string[]
  }

  type PictureUploadByBatchRequest = {
    category?: string
    count?: number
    namePrefix?: string
    offset?: number
    searchText?: string
    spaceId?: string | number
    tags?: string[]
  }

  type PictureUploadByUrlRequest = {
    category?: string
    fileUrl?: string
    id?: string | number
    introduction?: string
    name?: string
    spaceId?: string | number
    tags?: string[]
  }

  type PictureVO = {
    category?: string
    createTime?: string
    editTime?: string
    id?: string | number
    introduction?: string
    name?: string
    permissionList?: string[]
    picColor?: string
    picFormat?: string
    picHeight?: number
    picScale?: number
    picSize?: string | number
    picWidth?: number
    reviewMessage?: string
    reviewStatus?: number
    reviewTime?: string
    reviewerId?: string | number
    spaceId?: string | number
    tags?: string[]
    thumbnailUrl?: string
    updateTime?: string
    uploadStatus?: number
    url?: string
    user?: UserVO
    userId?: string | number
  }

  type Ranking = {
    spaceId?: string | number
    spaceName?: string
    usedCount?: string | number
    usedSize?: string | number
  }

  type resultUsingGETParams = {
    /** taskId */
    taskId?: string
  }

  type SearchPictureByColorRequest = {
    picColor?: string
    spaceId?: string | number
  }

  type SearchPictureByPictureRequest = {
    externalSearchConsent?: boolean
    pictureId?: string | number
  }

  type sourceUsingGETParams = {
    /** id */
    id?: string | number
  }

  type Space = {
    createTime?: string
    editTime?: string
    id?: string | number
    isDelete?: number
    maxCount?: string | number
    maxSize?: string | number
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    totalCount?: string | number
    totalSize?: string | number
    updateTime?: string
    userId?: string | number
  }

  type SpaceAddRequest = {
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
  }

  type SpaceAnalysisVO = {
    categories?: Distribution[]
    generatedAt?: string
    rankings?: Ranking[]
    scope?: string
    sizes?: Distribution[]
    spaceId?: string | number
    spaceName?: string
    tags?: Distribution[]
    trend?: Trend
    usage?: Usage
  }

  type SpaceAnalyzeRequest = {
    endDate?: string
    scope?: string
    spaceId?: string | number
    startDate?: string
    timeDimension?: string
    topN?: number
    userId?: string | number
  }

  type SpaceDeleteRequest = {
    id?: string | number
  }

  type SpaceEditRequest = {
    id?: string | number
    spaceName?: string
  }

  type SpaceLevelVO = {
    maxCount?: string | number
    maxSize?: string | number
    text?: string
    value?: number
  }

  type SpaceQueryRequest = {
    current?: number
    id?: string | number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    userId?: string | number
  }

  type SpaceUpdateRequest = {
    id?: string | number
    maxCount?: string | number
    maxSize?: string | number
    spaceLevel?: number
    spaceName?: string
  }

  type SpaceUserAddRequest = {
    spaceId?: string | number
    spaceRole?: string
    userId?: string | number
  }

  type SpaceUserEditRequest = {
    id?: string | number
    spaceId?: string | number
    spaceRole?: string
  }

  type SpaceUserQueryRequest = {
    current?: number
    pageSize?: number
    spaceId?: string | number
    userId?: string | number
  }

  type SpaceUserVO = {
    createTime?: string
    id?: string | number
    owner?: boolean
    space?: SpaceVO
    spaceId?: string | number
    spaceRole?: string
    userAvatar?: string
    userId?: string | number
    userName?: string
  }

  type SpaceVO = {
    canManageSpace?: boolean
    createTime?: string
    editTime?: string
    id?: string | number
    maxCount?: string | number
    maxSize?: string | number
    permissionList?: string[]
    spaceLevel?: number
    spaceName?: string
    spaceType?: number
    totalCount?: string | number
    totalSize?: string | number
    updateTime?: string
    userId?: string | number
  }

  type testFileDownloadUsingGETParams = {
    /** filepath */
    filepath?: string
  }

  type Trend = {
    endDate?: string
    points?: Distribution[]
    startDate?: string
    timeDimension?: string
    timezone?: string
  }

  type uploadPictureUsingPOSTParams = {
    category?: string
    id?: string | number
    introduction?: string
    name?: string
    spaceId?: string | number
    tags?: string[]
  }

  type Usage = {
    completedCount?: string | number
    countUsageRatio?: number
    draftCount?: string | number
    maxCount?: string | number
    maxSize?: string | number
    sizeUsageRatio?: number
    usedCount?: string | number
    usedSize?: string | number
  }

  type User = {
    createTime?: string
    editTime?: string
    id?: string | number
    isDelete?: number
    updateTime?: string
    userAccount?: string
    userAvatar?: string
    userName?: string
    userPassword?: string
    userProfile?: string
    userRole?: number
  }

  type UserAddRequest = {
    userAccount?: string
    userAvatar?: string
    userName?: string
    userPassword?: string
    userProfile?: string
    userRole?: number
  }

  type UserDeleteRequest = {
    id?: string | number
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    current?: number
    id?: string | number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    userAccount?: string
    userName?: string
    userProfile?: string
    userRole?: number
  }

  type UserRegisterRequest = {
    checkPassword?: string
    userAccount?: string
    userPassword?: string
  }

  type UserProfileUpdateRequest = { userName: string; userProfile?: string }
  type UserPasswordUpdateRequest = { oldPassword: string; newPassword: string; checkPassword: string }

  type UserUpdateRequest = {
    id?: string | number
    userAvatar?: string
    userName?: string
    userProfile?: string
    userRole?: number
  }

  type AdminUserVO = UserVO & { userAccount?: string; userRole?: number }
  type BaseResponseAdminUserVO_ = { code?: number; data?: AdminUserVO; message?: string }
  type BaseResponsePageAdminUserVO_ = { code?: number; data?: Omit<PageUserVO_, "records"> & { records?: AdminUserVO[] }; message?: string }

  type UserVO = {
    createTime?: string
    editTime?: string
    id?: string | number
    updateTime?: string
    userAvatar?: string
    userName?: string
    userProfile?: string
  }
}
