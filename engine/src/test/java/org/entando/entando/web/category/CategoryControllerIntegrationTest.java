/*
 * Copyright 2018-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */
package org.entando.entando.web.category;

import java.io.InputStream;

import com.agiletec.aps.system.SystemConstants;
import com.agiletec.aps.system.services.category.Category;
import com.agiletec.aps.system.services.category.ICategoryManager;
import com.agiletec.aps.system.services.user.UserDetails;
import com.agiletec.aps.util.FileTextReader;
import org.entando.entando.aps.servlet.security.CORSFilter;
import org.entando.entando.aps.system.services.category.ICategoryService;
import org.entando.entando.web.AbstractControllerIntegrationTest;
import org.entando.entando.web.category.validator.CategoryValidator;
import org.entando.entando.web.utils.OAuth2TestUtils;
import org.hamcrest.Matchers;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import static org.hamcrest.CoreMatchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class CategoryControllerIntegrationTest extends AbstractControllerIntegrationTest {

    @Autowired
    private ICategoryService categoryService;

    @Autowired
    private ICategoryManager categoryManager;

    @Autowired
    private CategoryController controller;

    @Test
    public void testGetCategories() throws Exception {
        UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
        String accessToken = mockOAuthInterceptor(user);
        ResultActions result = mockMvc
                .perform(get("/categories")
                        .header("Authorization", "Bearer " + accessToken));
        result.andExpect(status().isOk());
        result.andExpect(header().string("Access-Control-Allow-Origin", "*"));
        result.andExpect(header().string("Access-Control-Allow-Methods", CORSFilter.ALLOWED_METHODS));
        result.andExpect(header().string("Access-Control-Allow-Headers", "Content-Type, Authorization"));
        result.andExpect(header().string("Access-Control-Max-Age", "3600"));
    }

    @Test
    public void testGetValidCategoryTree() throws Exception {
        UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
        String accessToken = mockOAuthInterceptor(user);
        ResultActions result = mockMvc
                .perform(get("/categories")
                        .param("parentCode", "home")
                        .header("Authorization", "Bearer " + accessToken));
        result.andExpect(status().isOk());
    }

    @Test
    public void testGetInvalidCategoryTree() throws Exception {
        UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
        String accessToken = mockOAuthInterceptor(user);
        this.executeGet("invalid_code", accessToken, status().isNotFound());
        ResultActions result = mockMvc
                .perform(get("/categories")
                        .param("parentCode", "invalid_code")
                        .header("Authorization", "Bearer " + accessToken));
        result.andExpect(status().isNotFound());
    }

    @Test
    public void testGetValidCategory() throws Exception {
        UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
        String accessToken = mockOAuthInterceptor(user);
        this.executeGet("cat1", accessToken, status().isOk());
    }

    @Test
    public void testGetInvalidCategory() throws Exception {
        UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
        String accessToken = mockOAuthInterceptor(user);
        this.executeGet("invalid_code", accessToken, status().isNotFound());
    }

    @Test
    public void testAddCategory() throws Exception {
        String categoryCode = "test_cat";
        try {
            Assert.assertNotNull(this.categoryManager.getCategory("cat1"));
            Assert.assertNull(this.categoryManager.getCategory(categoryCode));
            UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
            String accessToken = mockOAuthInterceptor(user);
            this.executePost("1_POST_invalid_1.json", accessToken, status().isBadRequest());
            this.executePost("1_POST_invalid_2.json", accessToken, status().isBadRequest());
            this.executePost("1_POST_invalid_3.json", accessToken, status().isNotFound());
            this.executePost("1_POST_valid.json", accessToken, status().isOk());
            Assert.assertNotNull(this.categoryManager.getCategory(categoryCode));
            this.executeDelete(categoryCode, accessToken, status().isOk());
            Assert.assertNull(this.categoryManager.getCategory(categoryCode));
        } finally {
            this.categoryManager.deleteCategory(categoryCode);
        }
    }

    @Test
    public void testUpdateCategory() throws Exception {
        String categoryCode = "test_cat2";
        try {
            Assert.assertNotNull(this.categoryManager.getCategory("cat1"));
            Assert.assertNull(this.categoryManager.getCategory(categoryCode));
            UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
            String accessToken = mockOAuthInterceptor(user);
            ResultActions result = this.executePost("2_POST_valid.json", accessToken, status().isOk());
            result.andExpect(jsonPath("$.errors", Matchers.hasSize(0)));
            result.andExpect(jsonPath("$.payload.code", is(categoryCode)));

            result = this.executePut("2_PUT_invalid_1.json", categoryCode, accessToken, status().isBadRequest());
            result.andExpect(jsonPath("$.payload", Matchers.hasSize(0)));
            result.andExpect(jsonPath("$.errors[0].code", is(CategoryValidator.ERRCODE_PARENT_CATEGORY_CANNOT_BE_CHANGED)));

            result = this.executePut("2_PUT_invalid_2.json", categoryCode, accessToken, status().isBadRequest());
            result.andExpect(jsonPath("$.payload", Matchers.hasSize(0)));
            result.andExpect(jsonPath("$.errors[0].code", is("53")));

            result = this.executePut("2_PUT_valid.json", "home", accessToken, status().isBadRequest());
            result.andExpect(jsonPath("$.payload", Matchers.hasSize(0)));
            result.andExpect(jsonPath("$.errors[0].code", is(CategoryValidator.ERRCODE_URINAME_MISMATCH)));

            result = this.executePut("2_PUT_valid.json", categoryCode, accessToken, status().isOk());
            result.andExpect(jsonPath("$.errors", Matchers.hasSize(0)));
            result.andExpect(jsonPath("$.payload.code", is(categoryCode)));
            Category modified = this.categoryManager.getCategory(categoryCode);
            Assert.assertNotNull(modified);
            Assert.assertTrue(modified.getTitle("en").startsWith("New "));
            Assert.assertTrue(modified.getTitle("it").startsWith("Nuovo "));
            result = this.executeDelete(categoryCode, accessToken, status().isOk());
            result.andExpect(jsonPath("$.payload.code", is(categoryCode)));
            result.andExpect(jsonPath("$.errors", Matchers.hasSize(0)));
            Assert.assertNull(this.categoryManager.getCategory(categoryCode));
        } finally {
            this.categoryManager.deleteCategory(categoryCode);
        }
    }

    @Test
    public void testDeleteCategory() throws Exception {
        String categoryCode = "test_cat";
        try {
            Assert.assertNotNull(this.categoryManager.getCategory("cat1"));
            Assert.assertNull(this.categoryManager.getCategory(categoryCode));
            UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
            String accessToken = mockOAuthInterceptor(user);
            ResultActions result = this.executePost("1_POST_valid.json", accessToken, status().isOk());
            result.andExpect(jsonPath("$.payload.code", is(categoryCode)));
            Assert.assertNotNull(this.categoryManager.getCategory(categoryCode));
            result = this.executeDelete(categoryCode, accessToken, status().isOk());
            result.andExpect(jsonPath("$.payload.code", is(categoryCode)));
            Assert.assertNull(this.categoryManager.getCategory(categoryCode));
            result = this.executeDelete("invalid_category", accessToken, status().isOk());
            result.andExpect(jsonPath("$.payload.code", is("invalid_category")));
            result = this.executeDelete("cat1", accessToken, status().isBadRequest());
            result.andExpect(jsonPath("$.errors[0].code", is(CategoryValidator.ERRCODE_CATEGORY_REFERENCES)));
        } finally {
            this.categoryManager.deleteCategory(categoryCode);
        }
    }

    @Test
    public void testGetCategoryReferences() throws Exception {
        Assert.assertNotNull(this.categoryManager.getCategory("cat1"));
        Assert.assertNull(this.categoryManager.getCategory("test_test"));
        UserDetails user = new OAuth2TestUtils.UserBuilder("jack_bauer", "0x24").grantedToRoleAdmin().build();
        String accessToken = this.mockOAuthInterceptor(user);
        ResultActions result = this.executeReference("cat1", accessToken, SystemConstants.DATA_OBJECT_MANAGER, status().isOk());
        result.andExpect(jsonPath("$.payload", Matchers.hasSize(1)));
        result = this.executeReference("test_test", accessToken, SystemConstants.DATA_OBJECT_MANAGER, status().isNotFound());
        result.andExpect(jsonPath("$.payload", Matchers.hasSize(0)));
        result.andExpect(jsonPath("$.errors[0].code", is(CategoryValidator.ERRCODE_CATEGORY_NOT_FOUND)));
        result = this.executeReference("cat1", accessToken, SystemConstants.GROUP_MANAGER, status().isNotFound());
        result.andExpect(jsonPath("$.payload", Matchers.hasSize(0)));
        result.andExpect(jsonPath("$.errors[0].code", is(CategoryValidator.ERRCODE_CATEGORY_NO_REFERENCES)));
    }

    private ResultActions executeGet(String categoryCode, String accessToken, ResultMatcher rm) throws Exception {
        ResultActions result = mockMvc
                .perform(get("/categories/{categoryCode}", new Object[]{categoryCode})
                        .header("Authorization", "Bearer " + accessToken));
        result.andExpect(rm);
        return result;
    }

    private ResultActions executePost(String filename, String accessToken, ResultMatcher rm) throws Exception {
        InputStream isJsonPost = this.getClass().getResourceAsStream(filename);
        String jsonPost = FileTextReader.getText(isJsonPost);
        ResultActions result = mockMvc
                .perform(post("/categories").content(jsonPost)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .header("Authorization", "Bearer " + accessToken));
        result.andExpect(rm);
        return result;
    }

    private ResultActions executePut(String filename, String categoryCode, String accessToken, ResultMatcher rm) throws Exception {
        InputStream isJsonPut = this.getClass().getResourceAsStream(filename);
        String jsonPut = FileTextReader.getText(isJsonPut);
        ResultActions result = mockMvc
                .perform(put("/categories/{categoryCode}", new Object[]{categoryCode})
                        .content(jsonPut)
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .header("Authorization", "Bearer " + accessToken));
        result.andExpect(rm);
        return result;
    }

    private ResultActions executeDelete(String categoryCode, String accessToken, ResultMatcher rm) throws Exception {
        ResultActions result = mockMvc
                .perform(delete("/categories/{categoryCode}", new Object[]{categoryCode})
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .header("Authorization", "Bearer " + accessToken));
        result.andExpect(rm);
        return result;
    }

    private ResultActions executeReference(String categoryCode, String accessToken, String managerName, ResultMatcher rm) throws Exception {
        ResultActions result = mockMvc
                .perform(get("/categories/{categoryCode}/references/{holder}", new Object[]{categoryCode, managerName})
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .header("Authorization", "Bearer " + accessToken));
        result.andExpect(rm);
        return result;
    }

}
