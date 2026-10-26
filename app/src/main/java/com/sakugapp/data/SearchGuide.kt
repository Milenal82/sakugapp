// SPDX-License-Identifier: GPL-3.0-only
package com.sakugapp.data

import com.sakugapp.R

data class SearchExample(val query:String,val description:Int)

/** User-supplied reference, checked against public Moebooru query/parser sources. No local query evaluation. */
object SearchGuide {
    val examples=listOf(
        SearchExample("tag1 tag2",R.string.ui_both_tags_and),
        SearchExample("~tag1 ~tag2",R.string.ui_either_tag_or),
        SearchExample("-tag1",R.string.ui_exclude_a_tag),
        SearchExample("tag1*",R.string.ui_tag_prefix_wildcard),
        SearchExample("user:bob",R.string.ui_uploader_username),
        SearchExample("vote:3:bob",R.string.ui_user_s_favorites),
        SearchExample("md5:foo",R.string.ui_file_md5),
        SearchExample("rating:questionable",R.string.ui_rating_equals_questionable),
        SearchExample("-rating:questionable",R.string.ui_exclude_a_rating),
        SearchExample("source:http://site.com",R.string.ui_source_url_starts_with_this_prefix),
        SearchExample("id:100",R.string.ui_exact_post_id),
        SearchExample("id:100..",R.string.ui_id_at_least_100),
        SearchExample("id:>=100",R.string.ui_id_at_least_100),
        SearchExample("id:>100",R.string.ui_id_above_100),
        SearchExample("id:..100",R.string.ui_id_at_most_100),
        SearchExample("id:<=100",R.string.ui_id_at_most_100),
        SearchExample("id:<100",R.string.ui_id_below_100),
        SearchExample("id:100..200",R.string.ui_inclusive_id_range),
        SearchExample("width:100",R.string.ui_width_in_pixels),
        SearchExample("height:100",R.string.ui_height_in_pixels),
        SearchExample("score:100",R.string.ui_exact_score),
        SearchExample("mpixels:2.5..",R.string.ui_at_least_2_5_megapixels),
        SearchExample("date:2007-01-01",R.string.ui_upload_date),
        SearchExample("order:id",R.string.ui_id_ascending),
        SearchExample("order:id_desc",R.string.ui_id_descending),
        SearchExample("order:score",R.string.ui_score_descending),
        SearchExample("order:score_asc",R.string.ui_score_ascending),
        SearchExample("order:mpixels",R.string.ui_resolution_descending),
        SearchExample("order:mpixels_asc",R.string.ui_resolution_ascending),
        SearchExample("order:landscape",R.string.ui_landscape_first),
        SearchExample("order:portrait",R.string.ui_portrait_first),
        SearchExample("vote:3:bob order:vote",R.string.ui_favorite_time_requires_vote_search),
        SearchExample("parent:1234",R.string.ui_children_and_parent_post),
        SearchExample("parent:none",R.string.ui_posts_without_a_parent),
        SearchExample("gun* dress",R.string.ui_wildcard_combinations_may_be_limited),
        SearchExample("~gun dress",R.string.ui_mixed_or_combinations_may_be_limited),
        SearchExample("rating:questionable rating:safe",R.string.ui_avoid_repeated_fields),
        SearchExample("rating:questionable score:100.. id:..1000",R.string.ui_combine_different_fields)
    )
}
