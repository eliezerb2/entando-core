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
package org.entando.entando.aps.system.services.widgettype;

import java.util.ArrayList;
import java.util.List;

import com.agiletec.aps.system.common.FieldSearchFilter;
import com.agiletec.aps.system.common.IManager;
import com.agiletec.aps.system.common.model.dao.SearcherDaoPaginatedResult;
import com.agiletec.aps.system.exception.ApsSystemException;
import com.agiletec.aps.system.services.group.GroupUtilizer;
import com.agiletec.aps.system.services.group.IGroupManager;
import com.agiletec.aps.system.services.page.IPage;
import com.agiletec.aps.system.services.page.IPageManager;
import com.agiletec.aps.util.ApsProperties;
import org.apache.commons.lang3.StringUtils;
import org.entando.entando.aps.system.exception.RestRourceNotFoundException;
import org.entando.entando.aps.system.exception.RestServerError;
import org.entando.entando.aps.system.services.IDtoBuilder;
import org.entando.entando.aps.system.services.group.GroupServiceUtilizer;
import org.entando.entando.aps.system.services.guifragment.GuiFragment;
import org.entando.entando.aps.system.services.guifragment.IGuiFragmentManager;
import org.entando.entando.aps.system.services.widgettype.model.WidgetDto;
import org.entando.entando.web.common.exceptions.ValidationGenericException;
import org.entando.entando.web.common.model.PagedMetadata;
import org.entando.entando.web.common.model.RestListRequest;
import org.entando.entando.web.widget.model.WidgetRequest;
import org.entando.entando.web.widget.validator.WidgetValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.validation.BeanPropertyBindingResult;

@Service
public class WidgetService implements IWidgetService, GroupServiceUtilizer<WidgetDto> {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private IWidgetTypeManager widgetManager;

    private IPageManager pageManager;

    private IGuiFragmentManager guiFragmentManager;

    private IGroupManager groupManager;

    private IDtoBuilder<WidgetType, WidgetDto> dtoBuilder;

    @SuppressWarnings("rawtypes")
    @Override
    public PagedMetadata<WidgetDto> getWidgets(RestListRequest restListReq) {
        try {
            List<FieldSearchFilter> filters = new ArrayList<>(restListReq.buildFieldSearchFilters());
            filters.stream().filter(i -> i.getKey() != null)
                    .forEach(i -> i.setKey(WidgetDto.getEntityFieldName(i.getKey())));
            SearcherDaoPaginatedResult<WidgetType> widgets = this.getWidgetManager().getWidgetTypes(filters);
            List<WidgetDto> dtoList = dtoBuilder.convert(widgets.getList());
            for (WidgetDto widgetDto : dtoList) {
                this.addFragments(widgetDto);
            }
            PagedMetadata<WidgetDto> pagedMetadata = new PagedMetadata<>(restListReq, widgets);
            pagedMetadata.setBody(dtoList);
            return pagedMetadata;
        } catch (Throwable t) {
            logger.error("error in get widgets", t);
            throw new RestServerError("error in get widgets", t);
        }
    }

    @Override
    public WidgetDto getWidget(String widgetCode) {
        WidgetType widgetType = this.getWidgetManager().getWidgetType(widgetCode);
        if (null == widgetType) {
            logger.warn("no widget type found with code {}", widgetCode);
            throw new RestRourceNotFoundException(WidgetValidator.ERRCODE_WIDGET_NOT_FOUND, "widget type", widgetCode);
        }
        WidgetDto widgetDto = dtoBuilder.convert(widgetType);
        try {
            this.addFragments(widgetDto);
        } catch (Exception e) {
            logger.error("Failed to fetch gui fragment for widget type code ", e);
        }
        return widgetDto;
    }

    @Override
    public WidgetDto addWidget(WidgetRequest widgetRequest) {
        WidgetType widgetType = new WidgetType();
        this.processWidgetType(widgetType, widgetRequest);
        WidgetType oldWidgetType = this.getWidgetManager().getWidgetType(widgetType.getCode());
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(widgetType, "widget");
        if (null != oldWidgetType) {
            bindingResult.reject(WidgetValidator.ERRCODE_WIDGET_ALREADY_EXISTS, new String[]{widgetType.getCode()}, "widgettype.exists");
            throw new ValidationGenericException(bindingResult);
        } else if (null == this.getGroupManager().getGroup(widgetRequest.getGroup())) {
            bindingResult.reject(WidgetValidator.ERRCODE_WIDGET_GROUP_INVALID, new String[]{widgetRequest.getGroup()}, "widgettype.group.invalid");
            throw new ValidationGenericException(bindingResult);
        }
        WidgetDto widgetDto = null;
        try {
            this.getWidgetManager().addWidgetType(widgetType);
            this.createAndAddFragment(widgetType, widgetRequest);
            widgetDto = this.dtoBuilder.convert(widgetType);
            this.addFragments(widgetDto);
        } catch (Exception e) {
            logger.error("Failed to add widget type for request {} ", widgetRequest);
            throw new RestServerError("error in add widget", e);
        }
        return widgetDto;
    }

    private void createAndAddFragment(WidgetType widgetType, WidgetRequest widgetRequest) throws Exception {
        GuiFragment guiFragment = new GuiFragment();
        String code = this.extractUniqueGuiFragmentCode(widgetType.getCode());
        guiFragment.setCode(code);
        guiFragment.setPluginCode(widgetType.getPluginCode());
        guiFragment.setGui(widgetRequest.getCustomUi());
        guiFragment.setWidgetTypeCode(widgetType.getCode());
        this.getGuiFragmentManager().addGuiFragment(guiFragment);
    }

    protected String extractUniqueGuiFragmentCode(String widgetTypeCode) throws ApsSystemException {
        String uniqueCode = widgetTypeCode;
        if (null != this.getGuiFragmentManager().getGuiFragment(uniqueCode)) {
            int index = 0;
            String currentCode = null;
            do {
                index++;
                currentCode = uniqueCode + "_" + index;
            } while (null != this.getGuiFragmentManager().getGuiFragment(currentCode));
            uniqueCode = currentCode;
        }
        return uniqueCode;
    }

    @Override
    public WidgetDto updateWidget(String widgetCode, WidgetRequest widgetRequest) {
        WidgetType type = this.getWidgetManager().getWidgetType(widgetCode);
        if (type == null) {
            throw new RestRourceNotFoundException(WidgetValidator.ERRCODE_WIDGET_DOES_NOT_EXISTS, "widget", widgetCode);
        } else if (null == this.getGroupManager().getGroup(widgetRequest.getGroup())) {
            BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(type, "widget");
            bindingResult.reject(WidgetValidator.ERRCODE_WIDGET_GROUP_INVALID, new String[]{widgetRequest.getGroup()}, "widgettype.group.invalid");
            throw new ValidationGenericException(bindingResult);
        }
        this.processWidgetType(type, widgetRequest);
        WidgetDto widgetDto = dtoBuilder.convert(type);
        try {
            this.getWidgetManager().updateWidgetType(widgetCode, type.getTitles(), type.getConfig(), type.getMainGroup());
            if (!StringUtils.isEmpty(widgetCode)) {
                GuiFragment guiFragment = this.getGuiFragmentManager().getUniqueGuiFragmentByWidgetType(widgetCode);
                if (null == guiFragment) {
                    this.createAndAddFragment(type, widgetRequest);
                } else {
                    guiFragment.setGui(widgetRequest.getCustomUi());
                    this.getGuiFragmentManager().updateGuiFragment(guiFragment);
                }
            }
            this.addFragments(widgetDto);
        } catch (Throwable e) {
            logger.error("failed to update widget type", e);
            throw new RestServerError("Failed to update widget", e);
        }
        return widgetDto;
    }

    @Override
    public void removeWidget(String widgetCode) {
        try {
            WidgetType type = this.getWidgetManager().getWidgetType(widgetCode);
            BeanPropertyBindingResult validationResult = checkWidgetForDelete(type);
            if (validationResult.hasErrors()) {
                throw new ValidationGenericException(validationResult);
            }
            List<String> fragmentCodes = this.getGuiFragmentManager().getGuiFragmentCodesByWidgetType(widgetCode);
            for (String fragmentCode : fragmentCodes) {
                this.getGuiFragmentManager().deleteGuiFragment(fragmentCode);
            }
            this.getWidgetManager().deleteWidgetType(widgetCode);
        } catch (ApsSystemException e) {
            logger.error("Failed to remove widget type for request {} ", widgetCode);
            throw new RestServerError("failed to update widget type by code ", e);
        }
    }

    private void processWidgetType(WidgetType type, WidgetRequest widgetRequest) {
        type.setCode(widgetRequest.getCode());
        ApsProperties titles = new ApsProperties();
        widgetRequest.getTitles().forEach((k, v) -> titles.put(k, v));
        type.setTitles(titles);
        type.setMainGroup(widgetRequest.getGroup());
    }

    private void addFragments(WidgetDto widgetDto) throws Exception {
        List<String> fragmentCodes = this.getGuiFragmentManager().getGuiFragmentCodesByWidgetType(widgetDto.getCode());
        if (fragmentCodes != null) {
            for (String fragmentCode : fragmentCodes) {
                GuiFragment fragment = this.getGuiFragmentManager().getGuiFragment(fragmentCode);
                widgetDto.addGuiFragmentRef(fragment.getCode(), fragment.getCurrentGui(), fragment.getDefaultGui());
            }
        }
    }

    private BeanPropertyBindingResult checkWidgetForDelete(WidgetType widgetType) throws ApsSystemException {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(widgetType, "widget");
        if (null == widgetType) {
            return bindingResult;
        }
        if (widgetType.isLocked()) {
            bindingResult.reject(WidgetValidator.ERRCODE_CANNOT_DELETE_LOCKED, new String[]{widgetType.getCode()}, "widgettype.delete.locked");
        }
        List<IPage> onLinePages = this.getPageManager().getOnlineWidgetUtilizers(widgetType.getCode());
        List<IPage> draftPages = this.getPageManager().getDraftWidgetUtilizers(widgetType.getCode());
        if ((null != onLinePages && onLinePages.size() > 0) || (null != draftPages && draftPages.size() > 0)) {
            bindingResult.reject(WidgetValidator.ERRCODE_CANNOT_DELETE_USED_PAGES, new String[]{widgetType.getCode()}, "widgettype.delete.references.page");
        }
        return bindingResult;
    }

    @Override
    public String getManagerName() {
        return ((IManager) this.getWidgetManager()).getName();
    }

    @Override
    public List<WidgetDto> getGroupUtilizer(String groupCode) {
        try {
            List<WidgetType> list = ((GroupUtilizer<WidgetType>) this.getWidgetManager()).getGroupUtilizers(groupCode);
            return this.getDtoBuilder().convert(list);
        } catch (ApsSystemException ex) {
            logger.error("Error loading WidgetType references for group {}", groupCode, ex);
            throw new RestServerError("Error loading WidgetType references for group", ex);
        }
    }

    public IWidgetTypeManager getWidgetManager() {
        return widgetManager;
    }

    public void setWidgetManager(IWidgetTypeManager widgetManager) {
        this.widgetManager = widgetManager;
    }

    public IPageManager getPageManager() {
        return pageManager;
    }

    public void setPageManager(IPageManager pageManager) {
        this.pageManager = pageManager;
    }

    public IGroupManager getGroupManager() {
        return groupManager;
    }

    public void setGroupManager(IGroupManager groupManager) {
        this.groupManager = groupManager;
    }

    public IGuiFragmentManager getGuiFragmentManager() {
        return guiFragmentManager;
    }

    public void setGuiFragmentManager(IGuiFragmentManager guiFragmentManager) {
        this.guiFragmentManager = guiFragmentManager;
    }

    public IDtoBuilder<WidgetType, WidgetDto> getDtoBuilder() {
        return dtoBuilder;
    }

    public void setDtoBuilder(IDtoBuilder<WidgetType, WidgetDto> dtoBuilder) {
        this.dtoBuilder = dtoBuilder;
    }

}