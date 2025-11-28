package com.example.assignment.model;

public class Post {
    private Long id;
    private String title;
    private Long viewCount;

    public Post(Long id, String title, Long viewCount) {
        this.id = id;
        this.title = title;
        this.viewCount = viewCount;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public void setViewCount(Long viewCount) {
        this.viewCount = viewCount;
    }

    @Override
    public String toString() {
        return "Post{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", viewCount=" + viewCount +
                '}';
    }
}
