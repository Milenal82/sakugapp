// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.ui

/** Native interface text. Website content, tag names and query tokens are never translated. */
object TraditionalChinese {
    val strings=mapOf(
        "Back to app" to "返回應用程式", "Reload" to "重新載入", "Menu" to "選單",
        "Posts" to "貼文", "Blog" to "部落格", "Forum" to "論壇", "Language" to "語言",
        "My Account" to "我的帳戶", "Settings" to "設定", "About and licenses" to "關於與授權條款",
        "Retry" to "重試", "Tags · separate with spaces" to "標籤 · 以空格分隔", "Search" to "搜尋",
        "Filters" to "組合搜尋", "History" to "歷史紀錄", "Average color" to "平均色彩",
        "Retry this page" to "重試此頁", "No posts found." to "找不到符合條件的貼文。",
        "End of results" to "已顯示全部結果", "Load more" to "載入更多", "Search history" to "搜尋歷史紀錄",
        "No saved searches." to "尚無搜尋紀錄。", "Close" to "關閉", "Clear" to "清除", "Cancel" to "取消",
        "Network policy" to "網路設定", "Previews on metered networks are disabled in Settings." to "目前設定不允許在計量網路上預覽影片。",
        "Images: Wi-Fi only" to "圖片：僅限非計量網路", "Image unavailable" to "沒有可用的圖片",
        "Image failed to load" to "圖片載入失敗", "Uploader: " to "上傳者：", "Rating: " to "評等：",
        "Status: " to "狀態：", "Source: " to "來源：", "Open original post" to "開啟原始貼文",
        "No browser available." to "沒有可用的瀏覽器。", "Search filters" to "搜尋條件", "Order" to "排序",
        "Apply" to "套用", "Exact frame analysis" to "嚴格逐幀分析", "Previous frame" to "上一幀", "Next frame" to "下一幀",
        "Downloads the original video into private cache (up to 200 MiB). Android 9+ and a decoder supporting frame indexes are required. Starts at actual frame 1, independent of playback position." to "將原始影片下載至私人快取（最多 200 MiB）。需要 Android 9 以上及支援幀索引的解碼器。從實際第 1 幀開始，與播放時間位置分開。",
        "Download and decode" to "下載並解碼", "Notices" to "來源與聲明", "GPL license" to "GPL 授權條款",
        "Sakugapp contributors · GPL-3.0-only\nNo warranty. Modification and redistribution are permitted under the license." to "Sakugapp 貢獻者 · GPL-3.0-only\n本軟體不提供擔保。可依授權條款修改及重新散布。",
        "App language" to "應用程式語言", "The official website supports English and Japanese. Traditional Chinese translates the native app; official pages use English." to "官網支援英語及日語。繁體中文用於原生介面；官網頁面會使用英語。",
        "Appearance" to "外觀", "Light background" to "淺色背景", "Crop rectangular thumbnails" to "裁切長方形預覽圖",
        "Score shortcuts" to "Score 快捷條件", "Artist wiki: " to "動畫師 Wiki：",
        "Download video" to "下載影片", "Keep this page open while downloading." to "下載時請保持此頁面開啟。",
        "Cancel download" to "取消下載", "Video saved." to "影片已儲存。", "Download cancelled." to "已取消下載。",
        "Video download failed. Please retry." to "影片下載失敗，請重試。", "Empty media response." to "媒體回應為空。",
        "The website did not return a video file." to "網站沒有回傳影片檔案。", "The video download is incomplete." to "影片下載不完整。",
        "Wrap tags" to "標籤自動換行", "Copyright tags" to "版權標籤", "Animator tags" to "動畫師標籤",
        "Video" to "影片", "Auto preview visible posts" to "自動預覽畫面中的貼文",
        "Allow previews on metered networks" to "允許在計量網路上預覽", "Default mute" to "預設靜音",
        "Default loop" to "預設循環播放", "Enable snapshot sharing" to "啟用畫面截圖分享", "Mirror video" to "水平翻轉影片",
        "Default playback speed" to "預設播放速度", "Brightness" to "亮度", "Contrast" to "對比度", "Saturation" to "飽和度",
        "Reset video effects" to "重設影片效果", "Network and storage" to "網路與儲存空間",
        "Load images only on unmetered networks" to "僅在非計量網路載入圖片", "Clear media cache" to "清除媒體快取",
        "Clear search history" to "清除搜尋歷史紀錄", "Cache cleared." to "已清除快取。",
        "Unable to clear all cache files." to "無法清除所有快取檔案。", "Search history cleared." to "已清除搜尋歷史紀錄。",
        "Saved website session. Protected actions are confirmed by the server; expired sessions may require login again." to "已儲存官網登入狀態。需要權限的操作由伺服器確認；登入狀態過期時需重新登入。",
        "Official account management" to "官網帳戶管理", "Log out on this device" to "在此裝置登出",
        "Username" to "使用者名稱", "Password" to "密碼", "Log in" to "登入",
        "Uses the official HTTPS login endpoint. Your password is not stored. Session cookies are encrypted with Android Keystore. Registration and password reset are handled on the official site." to "使用官網 HTTPS 登入介面，不儲存密碼。登入 Cookie 以 Android Keystore 加密。註冊及重設密碼請於官網完成。",
        "Registration / reset on website" to "前往官網註冊／重設密碼", "Vote" to "評分",
        "Log in to vote or comment" to "登入後即可評分或留言", "0 clears your vote · 3 is the site's favorite level" to "0 表示取消評分 · 3 為網站的收藏等級",
        "Reload vote" to "重新讀取評分", "Comments" to "留言", "Comment" to "留言內容", "Edit your comment" to "編輯你的留言",
        "Post comment" to "發表留言", "Save edit" to "儲存變更", "Cancel edit" to "取消編輯", "No comments on this post." to "此貼文尚無留言。",
        "Edit" to "編輯", "Delete" to "刪除", "Retry comments" to "重試載入留言", "More comments" to "更多留言",
        "Delete this comment?" to "確定刪除此留言？", "Vote confirmed by the server." to "伺服器已確認評分。",
        "Comment accepted by the server." to "伺服器已接受留言。", "Deletion confirmed by the server." to "伺服器已確認刪除。",
        "Unknown" to "未知", "Unmute" to "取消靜音", "Mute" to "靜音", "Loop on" to "循環播放：開", "Loop off" to "循環播放：關",
        "Exit full screen" to "退出全螢幕", "Full screen" to "全螢幕", "Exact frames" to "嚴格逐幀", "Snapshot" to "截圖",
        "Volume" to "音量", "Video is in full screen" to "影片正以全螢幕顯示", "Share frame" to "分享畫面",
        "No decoded frame is available to capture." to "尚無可供擷取的解碼畫面。", "Unable to share the screenshot." to "無法分享截圖。",
        "Frame by frame" to "逐幀播放／分析", "Frame number" to "幀編號", "Go to frame" to "跳至指定幀",
        "Use the actual decoded frame index, not a time estimate." to "依實際解碼幀索引前後切換，不使用時間估算。",
        "Loading license…" to "正在載入授權條款…", "Exact indexing requires Android 9 or later." to "嚴格幀索引需要 Android 9 或更新版本。",
        "Tag categories unavailable. Unknown categories are not guessed." to "無法取得標籤分類，不會推測未知分類。", "Reload posts and tags" to "重新載入貼文與標籤",
        "Search tools" to "組合搜尋工具", "Compose query" to "編輯搜尋式", "Search reference" to "搜尋指令備忘錄",
        "Examples replace the draft; Apply runs the search. Fields and operators are sent to the website unchanged." to "點選範例會取代搜尋草稿；按「套用」才會搜尋。欄位及運算子會原樣送至官網。",
        "Wildcard and mixed OR combinations have website limitations. Repeated fields cannot express multiple values; different fields can be combined." to "模糊搜尋及混合 OR 搜尋有網站限制。同類型欄位無法同時使用；不同欄位可組合使用。",
        "Query" to "搜尋式", "Tag or field condition" to "標籤或欄位條件", "AND · all tags" to "AND · 同時符合",
        "OR · ~tag" to "OR · 任一標籤", "Exclude · -tag" to "排除 · 不包含", "Add condition" to "加入條件",
        "OR applies to ordinary tags. Enter field conditions such as -rating:questionable directly in Query." to "OR 適用於一般標籤。-rating:questionable 等欄位條件請直接輸入搜尋式。",
        "Keep current" to "保留目前條件", "Website default" to "網站預設排序", "Rating" to "評等", "Clear rating filter" to "清除評等條件",
        "Wildcard combinations may be limited by the website." to "模糊搜尋無法與其他標籤良好搭配，結果以網站支援為準。",
        "Mixing OR tags with required or excluded tags may be limited by the website." to "OR 搜尋與必須符合／排除標籤混用時，結果以網站支援為準。",
        "Repeated fields are ambiguous; use one value or range per field." to "相同類型欄位無法同時使用，請改用單一值或範圍。",
        "order:vote requires a vote:score:user search." to "order:vote 僅適用於 vote:分數:使用者 搜尋。",
        "Network connection failed. Check the connection and retry." to "網路連線失敗，請檢查連線後重試。",
        "Network request failed. Check the connection and retry." to "網路請求失敗，請檢查連線後重試。",
        "Invalid post response; no substitute data is shown." to "貼文回應格式不正確，不會顯示替代資料。",
        "Unable to parse posts; no substitute data is shown." to "無法解析貼文，不會顯示替代資料。",
        "Unable to parse tag categories." to "無法解析標籤分類。", "Post not found." to "找不到此貼文。",
        "Rate limited. Please wait before retrying." to "網站限制了請求頻率，請稍後重試。",
        "Unable to read the network response." to "無法讀取網路回應。", "Unable to read the website response." to "無法讀取網站回應。",
        "The server returned an empty response." to "伺服器回應為空。", "The server response is too large." to "伺服器回應超過大小限制。",
        "Empty website response." to "網站回應為空。", "Website response is too large." to "網站回應超過大小限制。",
        "Frame analysis supports video up to 3840×2160 pixels." to "逐幀分析支援最多 3840×2160 像素的影片。",
        "This device/format does not expose a decoded frame count. No estimated FPS stepping is used." to "此裝置或格式無法提供實際幀數，不會使用估算 FPS 的跳轉代替逐幀。",
        "This video exceeds the 200 MiB analysis limit." to "影片超過 200 MiB 的分析限制。",
        "Video exceeds 200 MiB." to "影片超過 200 MiB。", "Unable to cache the video." to "無法快取影片。",
        "The device decoder could not return this exact frame." to "裝置解碼器無法取得這一幀。"
    )
    fun get(en:String):String = strings[en] ?: when {
        en.startsWith("Playback failed: ") -> "播放失敗："+en.substringAfter(": ")
        en.startsWith("Page load failed (") -> "頁面載入失敗（"+en.substringAfter('(').removeSuffix(".").removeSuffix(")")+"）。"
        en.startsWith("Session expired or this account lacks permission (HTTP ") -> "登入狀態已過期或帳戶沒有此權限（HTTP "+en.substringAfter("HTTP ").removeSuffix(".").removeSuffix(")")+"）。"
        en.startsWith("Access denied (HTTP ") -> "存取遭拒（HTTP "+en.substringAfter("HTTP ").substringBefore(')')+"）。請稍後重試或開啟原網站；不會繞過存取限制。"
        en.startsWith("Rate limited (HTTP ") -> "網站限制了請求頻率（HTTP "+en.substringAfter("HTTP ").substringBefore(')')+"），請稍後重試。"
        en.startsWith("The website is temporarily unavailable (HTTP ") || en.startsWith("Website request failed (HTTP ") || en.startsWith("Request failed (HTTP ") -> "網站請求失敗（HTTP "+en.substringAfter("HTTP ").substringBefore(')')+"）。"
        en.startsWith("Media download rejected (HTTP ") -> "媒體下載遭拒（HTTP "+en.substringAfter("HTTP ").substringBefore(')')+"）。"
        else -> en
    }
}
