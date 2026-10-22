// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

data class SearchExample(val query:String,val en:String,val ja:String,val zh:String)

/** User-supplied reference, checked against public Moebooru query/parser sources. No local query evaluation. */
object SearchGuide {
    val examples=listOf(
        SearchExample("tag1 tag2","Both tags (AND)","両方のタグ（AND）","搜尋同時有 tag1 和 tag2 的貼文"),
        SearchExample("~tag1 ~tag2","Either tag (OR)","いずれかのタグ（OR）","搜尋有 tag1 或 tag2 的貼文"),
        SearchExample("-tag1","Exclude a tag","タグを除外","搜尋不包含 tag1 的貼文"),
        SearchExample("tag1*","Tag prefix wildcard","タグの前方一致","搜尋標籤開頭為 tag1 的貼文"),
        SearchExample("user:bob","Uploader username","投稿ユーザー","搜尋由使用者 bob 上傳的貼文"),
        SearchExample("vote:3:bob","User's favorites","ユーザーのお気に入り","搜尋被使用者 bob 收藏的貼文"),
        SearchExample("md5:foo","File MD5","ファイルのMD5","搜尋 MD5 碼為 foo 的貼文"),
        SearchExample("rating:questionable","Rating equals questionable","指定レーティング","搜尋評等為 questionable 的貼文"),
        SearchExample("-rating:questionable","Exclude a rating","指定レーティングを除外","搜尋評等不是 questionable 的貼文"),
        SearchExample("source:http://site.com","Source URL starts with this prefix","ソースURLの前方一致","搜尋來源網址以 http://site.com 開頭的貼文"),
        SearchExample("id:100","Exact post ID","投稿IDと一致","搜尋 ID 為 100 的貼文"),
        SearchExample("id:100..","ID at least 100","IDが100以上","搜尋 ID 為 100 或以上的貼文"),
        SearchExample("id:>=100","ID at least 100","IDが100以上","搜尋 ID 為 100 或以上的貼文（同上）"),
        SearchExample("id:>100","ID above 100","IDが100より大きい","搜尋 ID 大於 100 的貼文"),
        SearchExample("id:..100","ID at most 100","IDが100以下","搜尋 ID 為 100 或以下的貼文"),
        SearchExample("id:<=100","ID at most 100","IDが100以下","搜尋 ID 為 100 或以下的貼文（同上）"),
        SearchExample("id:<100","ID below 100","IDが100未満","搜尋 ID 小於 100 的貼文"),
        SearchExample("id:100..200","Inclusive ID range","IDの範囲（両端を含む）","搜尋 ID 在 100 到 200 之間的貼文"),
        SearchExample("width:100","Width in pixels","幅（ピクセル）","搜尋寬度為 100 像素的貼文"),
        SearchExample("height:100","Height in pixels","高さ（ピクセル）","搜尋高度為 100 像素的貼文"),
        SearchExample("score:100","Exact score","スコアと一致","搜尋得分為 100 的貼文"),
        SearchExample("mpixels:2.5..","At least 2.5 megapixels","250万画素以上","搜尋像素數為 2.5 百萬或以上的貼文"),
        SearchExample("date:2007-01-01","Upload date","投稿日","搜尋上傳日期為 2007-01-01 的貼文"),
        SearchExample("order:id","ID ascending","IDの昇順","依 ID 升序排列搜尋結果"),
        SearchExample("order:id_desc","ID descending","IDの降順","依 ID 降序排列搜尋結果"),
        SearchExample("order:score","Score descending","スコアの降順","依得分降序排列搜尋結果"),
        SearchExample("order:score_asc","Score ascending","スコアの昇順","依得分升序排列搜尋結果"),
        SearchExample("order:mpixels","Resolution descending","解像度の降順","依解析度降序排列搜尋結果"),
        SearchExample("order:mpixels_asc","Resolution ascending","解像度の昇順","依解析度升序排列搜尋結果"),
        SearchExample("order:landscape","Landscape first","横長を優先","橫向圖片排前"),
        SearchExample("order:portrait","Portrait first","縦長を優先","直向圖片排前"),
        SearchExample("vote:3:bob order:vote","Favorite time (requires vote search)","お気に入り日時順（vote条件が必要）","依收藏時間排序（僅限 vote 搜尋）"),
        SearchExample("parent:1234","Children and parent post","親投稿と子投稿","搜尋母貼為 1234 的貼文（包含該母貼）"),
        SearchExample("parent:none","Posts without a parent","親投稿のない投稿","搜尋沒有母貼的貼文"),
        SearchExample("gun* dress","Wildcard combinations may be limited","ワイルドカードの組み合わせには制限あり","模糊搜尋無法與其他標籤良好搭配"),
        SearchExample("~gun dress","Mixed OR combinations may be limited","ORと通常タグの混在には制限あり","或搜尋無法與其他標籤良好搭配"),
        SearchExample("rating:questionable rating:safe","Avoid repeated fields","同種フィールドの重複は避ける","相同類型欄位無法同時使用"),
        SearchExample("rating:questionable score:100.. id:..1000","Combine different fields","異なるフィールドは組み合わせ可能","不同欄位可同時使用")
    )
}
