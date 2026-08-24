package com.lamndt.smartmovie.multiplatform.model

enum class AppLocale(val tag: String, val backendTag: String, val nativeName: String) {
    ENGLISH("en", "en-US", "English"),
    VIETNAMESE("vi", "vi-VN", "Tiếng Việt"),
    JAPANESE("ja", "ja-JP", "日本語"),
    KOREAN("ko", "ko-KR", "한국어"),
    CHINESE_SIMPLIFIED("zh-CN", "zh-CN", "简体中文"),
    CHINESE_TRADITIONAL("zh-TW", "zh-TW", "繁體中文");

    companion object {
        fun fromTag(value: String?): AppLocale = entries.firstOrNull {
            it.tag.equals(value, ignoreCase = true) || it.backendTag.equals(value, ignoreCase = true)
        } ?: ENGLISH
    }
}

data class UiStrings(
    val home: String,
    val explore: String,
    val search: String,
    val library: String,
    val profile: String,
    val movies: String,
    val tvSeries: String,
    val discover: String,
    val searchHint: String,
    val all: String,
    val favorites: String,
    val watchlist: String,
    val favorite: String,
    val watchLater: String,
    val removeFavorite: String,
    val removeWatchlist: String,
    val trailer: String,
    val details: String,
    val story: String,
    val cast: String,
    val similar: String,
    val rating: String,
    val year: String,
    val sort: String,
    val popularity: String,
    val topRated: String,
    val releaseDate: String,
    val reset: String,
    val retry: String,
    val loading: String,
    val noResults: String,
    val emptyLibrary: String,
    val serviceError: String,
    val language: String,
    val platformEdition: String,
    val loadMore: String,
    val close: String,
)

fun strings(locale: AppLocale): UiStrings = when (locale) {
    AppLocale.ENGLISH -> UiStrings(
        "Home", "Explore", "Search", "Library", "Profile", "Movies", "TV Series", "Discover",
        "Search titles, people, collections, companies and keywords", "All", "Favorites", "Watchlist", "Favorite", "Watch later",
        "Remove favorite", "Remove from watchlist", "Play trailer", "View details", "Story", "Cast",
        "Similar titles", "Rating", "Year", "Sort", "Popularity", "Top rated", "Release date", "Reset",
        "Try again", "Loading…", "No titles found", "Your library is ready for its first title",
        "SmartMovie could not reach the catalog", "Language", "Multiplatform edition", "Load more", "Close",
    )
    AppLocale.VIETNAMESE -> UiStrings(
        "Trang chủ", "Khám phá", "Tìm kiếm", "Thư viện", "Hồ sơ", "Phim điện ảnh", "Phim bộ", "Khám phá",
        "Tìm phim, con người, bộ sưu tập, công ty và từ khóa", "Tất cả", "Yêu thích", "Xem sau", "Yêu thích", "Xem sau",
        "Bỏ yêu thích", "Bỏ khỏi xem sau", "Xem trailer", "Xem chi tiết", "Câu chuyện", "Diễn viên",
        "Tựa phim tương tự", "Điểm", "Năm", "Sắp xếp", "Phổ biến", "Đánh giá cao", "Ngày phát hành", "Đặt lại",
        "Thử lại", "Đang tải…", "Không tìm thấy tựa phim", "Thư viện đang chờ tựa phim đầu tiên",
        "SmartMovie không thể kết nối danh mục", "Ngôn ngữ", "Phiên bản đa nền tảng", "Tải thêm", "Đóng",
    )
    AppLocale.JAPANESE -> UiStrings(
        "ホーム", "見つける", "検索", "ライブラリ", "プロフィール", "映画", "テレビ", "作品を探す",
        "作品、人物、コレクション、会社、キーワードを検索", "すべて", "お気に入り", "ウォッチリスト", "お気に入り", "後で見る",
        "お気に入りを解除", "リストから削除", "予告編を再生", "詳細を見る", "ストーリー", "キャスト",
        "関連作品", "評価", "年", "並び替え", "人気順", "高評価", "公開日", "リセット",
        "再試行", "読み込み中…", "作品が見つかりません", "最初の作品をライブラリに追加しましょう",
        "カタログに接続できません", "言語", "マルチプラットフォーム版", "さらに読み込む", "閉じる",
    )
    AppLocale.KOREAN -> UiStrings(
        "홈", "둘러보기", "검색", "라이브러리", "프로필", "영화", "TV 시리즈", "작품 찾기",
        "작품, 인물, 컬렉션, 회사 및 키워드 검색", "전체", "즐겨찾기", "관심 목록", "즐겨찾기", "나중에 보기",
        "즐겨찾기 해제", "관심 목록에서 삭제", "예고편 재생", "상세 보기", "줄거리", "출연진",
        "비슷한 작품", "평점", "연도", "정렬", "인기순", "평점순", "공개일", "초기화",
        "다시 시도", "불러오는 중…", "작품을 찾을 수 없습니다", "첫 작품을 라이브러리에 추가해 보세요",
        "카탈로그에 연결할 수 없습니다", "언어", "멀티플랫폼 에디션", "더 불러오기", "닫기",
    )
    AppLocale.CHINESE_SIMPLIFIED -> UiStrings(
        "首页", "探索", "搜索", "片库", "个人资料", "电影", "剧集", "发现作品",
        "搜索作品、人物、合集、公司和关键词", "全部", "收藏", "想看", "收藏", "稍后观看",
        "取消收藏", "移出想看", "播放预告", "查看详情", "剧情", "演员",
        "相似作品", "评分", "年份", "排序", "热门", "高分", "上映日期", "重置",
        "重试", "加载中…", "未找到作品", "把第一部作品加入片库吧",
        "SmartMovie 无法连接目录", "语言", "多平台版本", "加载更多", "关闭",
    )
    AppLocale.CHINESE_TRADITIONAL -> UiStrings(
        "首頁", "探索", "搜尋", "片庫", "個人資料", "電影", "影集", "探索作品",
        "搜尋作品、人物、合輯、公司和關鍵字", "全部", "收藏", "待看清單", "收藏", "稍後觀看",
        "取消收藏", "移出待看", "播放預告", "查看詳情", "故事", "演員",
        "相似作品", "評分", "年份", "排序", "熱門", "高評分", "上映日期", "重設",
        "再試一次", "載入中…", "找不到作品", "將第一部作品加入片庫吧",
        "SmartMovie 無法連線至目錄", "語言", "多平台版本", "載入更多", "關閉",
    )
}
