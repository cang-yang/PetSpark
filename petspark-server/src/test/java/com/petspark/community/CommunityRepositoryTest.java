package com.petspark.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.petspark.common.api.PageResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class CommunityRepositoryTest {

    @Test
    void insertAndRemoveInteractionChangesFlagsAndCounters() {
        JdbcTemplate jdbc = new JdbcTemplate(h2DataSource());
        createSchema(jdbc);
        CommunityRepository repository = new CommunityRepository(jdbc);
        String userId = "user-1";
        String postId = "post-1";
        jdbc.update("INSERT INTO sys_user (id, nickname) VALUES (?, ?)", userId, "小花");
        jdbc.update("INSERT INTO community_post (id, author_user_id, title, content, status) VALUES (?, ?, ?, ?, 'PUBLISHED')",
                postId, userId, "晒猫", "内容");

        boolean inserted = repository.insertInteraction(UUID.randomUUID().toString(), postId, userId, "LIKE");
        repository.incrementInteractionCount(postId, "LIKE");
        boolean duplicate = repository.insertInteraction(UUID.randomUUID().toString(), postId, userId, "LIKE");
        CommunityDtos.PostView liked = repository.loadPostView(postId, userId, false);

        assertThat(inserted).isTrue();
        assertThat(duplicate).isFalse();
        assertThat(liked.liked()).isTrue();
        assertThat(liked.likeCount()).isEqualTo(1);

        boolean deleted = repository.deleteInteraction(postId, userId, "LIKE");
        repository.decrementInteractionCount(postId, "LIKE");
        CommunityDtos.PostView unliked = repository.loadPostView(postId, userId, false);

        assertThat(deleted).isTrue();
        assertThat(unliked.liked()).isFalse();
        assertThat(unliked.likeCount()).isZero();
    }

    @Test
    void publishedListExcludesHiddenPostsButAdminListIncludesThem() {
        JdbcTemplate jdbc = new JdbcTemplate(h2DataSource());
        createSchema(jdbc);
        CommunityRepository repository = new CommunityRepository(jdbc);
        jdbc.update("INSERT INTO sys_user (id, nickname) VALUES ('u1', '用户')");
        jdbc.update("INSERT INTO community_post (id, author_user_id, title, content, status) VALUES ('p1', 'u1', '公开', '内容', 'PUBLISHED')");
        jdbc.update("INSERT INTO community_post (id, author_user_id, title, content, status) VALUES ('p2', 'u1', '隐藏', '内容', 'HIDDEN')");

        CommunityDtos.PostQuery query = new CommunityDtos.PostQuery();
        PageResult<CommunityDtos.PostView> published = repository.findPublishedPosts(query, "u1");
        PageResult<CommunityDtos.PostView> admin = repository.findAdminPosts(new CommunityDtos.PostQuery());

        assertThat(published.getTotal()).isEqualTo(1);
        assertThat(published.getItems()).extracting(CommunityDtos.PostView::title).containsExactly("公开");
        assertThat(admin.getTotal()).isEqualTo(2);
    }

    private DriverManagerDataSource h2DataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUsername("sa");
        ds.setPassword("");
        return ds;
    }

    private void createSchema(JdbcTemplate jdbc) {
        jdbc.execute("CREATE TABLE sys_user (id VARCHAR(36) PRIMARY KEY, nickname VARCHAR(64) NOT NULL)");
        jdbc.execute("""
                CREATE TABLE community_post (
                  id VARCHAR(36) PRIMARY KEY,
                  author_user_id VARCHAR(36) NOT NULL,
                  title VARCHAR(120) NOT NULL,
                  content CLOB NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  moderation_reason VARCHAR(255),
                  like_count INT NOT NULL DEFAULT 0,
                  favorite_count INT NOT NULL DEFAULT 0,
                  comment_count INT NOT NULL DEFAULT 0,
                  version INT NOT NULL DEFAULT 0,
                  deleted_at TIMESTAMP NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE community_comment (
                  id VARCHAR(36) PRIMARY KEY,
                  post_id VARCHAR(36) NOT NULL,
                  parent_id VARCHAR(36),
                  author_user_id VARCHAR(36) NOT NULL,
                  content VARCHAR(1000) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  moderation_reason VARCHAR(255),
                  version INT NOT NULL DEFAULT 0,
                  deleted_at TIMESTAMP NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE community_interaction (
                  id VARCHAR(36) PRIMARY KEY,
                  post_id VARCHAR(36) NOT NULL,
                  user_id VARCHAR(36) NOT NULL,
                  type VARCHAR(16) NOT NULL,
                  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                  UNIQUE (post_id, user_id, type)
                )
                """);
    }
}
