package com.yupi.springbootinit.model.dto.knowledgegraph;

import lombok.Data;

@Data
public class CategoryDTO {
    private String name; // 类别名称
    public CategoryDTO()
    {

    }
    public CategoryDTO(String name)
    {
        this.name=name;
    }
}
