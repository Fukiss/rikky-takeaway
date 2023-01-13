package com.fubukiss.rikky.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fubukiss.rikky.common.R;
import com.fubukiss.rikky.dto.DishDto;
import com.fubukiss.rikky.entity.Category;
import com.fubukiss.rikky.entity.Dish;
import com.fubukiss.rikky.service.CategoryService;
import com.fubukiss.rikky.service.DishFlavorService;
import com.fubukiss.rikky.service.DishService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>Project: rikky-takeaway - DishController 菜品相关的控制类
 * <p>Powered by river On 2023/01/12 2:50 PM
 *
 * @author Riverify
 * @version 1.0
 * @since JDK8
 */
@RestController
@Slf4j
@RequestMapping("/dish")
public class DishController {

    /**
     * 菜品服务
     */
    @Autowired
    private DishService dishService;
    /**
     * 菜品口味服务
     */
    @Autowired
    private DishFlavorService dishFlavorService;
    /**
     * 分类服务
     */
    @Autowired
    private CategoryService categoryService;


    /**
     * <h2>新增菜品<h2/>
     *
     * @param dishDto dto: data transfer object，主要用于多表查询时，将查询结果封装成一个对象，方便前端使用，如在本项目的菜品新增中，
     *                前端需要传入菜品的基本信息，以及菜品的口味信息，而菜品和菜品口味是两张表，在后端拥有两个实体类，
     *                所以需要将这两个实体类封装成一个对象。@RequestBody注解用于将前端传入的json数据转换成对象
     * @return 通用返回对象
     */
    @PostMapping
    public R<String> save(@RequestBody DishDto dishDto) {
        log.info("新增菜品，dishDto: {}", dishDto.toString());       // Slf4j的日志输出
        // 保存菜品
        dishService.saveWithFlavors(dishDto);   // saveWithFlavors方法为非mybatis-plus提供的方法，用于同时保存菜品和菜品口味

        return R.success("新增菜品成功");
    }


    /**
     * <h2>分页查询菜品<h2/>
     * <p>其中菜品的图片由{@link CommonController}提供下载到页面的功能。
     *
     * @param page     前端传入的分页参数，一次性传入当前页码
     * @param pageSize 前端传入的分页参数，一次性传入每页显示的条数
     * @param name     查询条件，如果name为空，则查询所有菜品
     * @return Page对象，mybatis-plus提供的分页对象，包含了分页的所有信息
     */
    @GetMapping("/page")
    public R<Page> page(int page, int pageSize, String name) {
        // 构造分页构造对象
        Page<Dish> pageInfo = new Page<>(page, pageSize);   // Page对象的构造方法需要传入当前页码和每页显示的条数
        Page<DishDto> dishDtoPage = new Page<>();   // disDto中比dish多了flavorsName字段，用于存储菜品口味的名称，因为只用dish对象无法获取菜品口味的名称，所以先创建一个空的Page对象
        // 条件构造器
        LambdaQueryWrapper<Dish> wrapper = new LambdaQueryWrapper<>();
        // 如果name不为空，则添加查询条件 (where name like '%name%')
        wrapper.like(name != null, Dish::getName, name);  // like方法的第一个参数为是否添加查询条件，第二个参数为查询的字段(Dish中的name)，第三个参数为查询的值(参数name)
        // 添加排序条件 (order by update-time desc)
        wrapper.orderByDesc(Dish::getUpdateTime);
        // 分页查询
        dishService.page(pageInfo, wrapper);   // page方法的第一个参数为分页构造对象，第二个参数为条件构造器
        // 对象拷贝，将pageInfo中的dish对象拷贝到dishDtoPage中的dishDto对象中
        BeanUtils.copyProperties(pageInfo, dishDtoPage, "records"); // BeanUtils是Spring提供的工具类，用于对象拷贝，第一个参数为源对象，第二个参数为目标对象，
        // 第三个参数为忽略的字段，之所以忽略records字段是因为两个records字段的类型不一致，无法直接拷贝
        // records是Page对象中的一个字段，用于存储分页查询的结果，因为dishDtoPage中的records字段是空的，所以需要手动将dishDtoPage中的records字段赋值
        List<Dish> records = pageInfo.getRecords();

        // 将records中的每个dish对象的categoryId经过查询出categoryName，然后将categoryName赋值给dishDto对象的categoryName字段，同时将其它字段也赋值给dishDto对象，返回的是一个List<DishDto>对象
        List<DishDto> dishDtoRecordsList = records.stream().map((item) -> {  // item是records List中的每一个元素，即每一个dish对象，其中却少了菜品口味的名称属性(categoryName)，于是我们需要将其变为dishDto对象，再利用CategoryService通过dish中的categoryId获取菜品口味的名称
            // 1.new DishDto()是为了将dish对象转换为dishDto对象，因为dishDto中多了flavorsName字段，用于存储菜品口味的名称
            DishDto dishDto = new DishDto();
            // 2.先进行dishDto的普通字段拷贝
            BeanUtils.copyProperties(item, dishDto);
            // 3.再进行dishDto的flavorsName字段拷贝
            Long categoryId = item.getCategoryId();                     // 获取page里面records的每一个dish对象的categoryId
            Category category = categoryService.getById(categoryId);    // 根据categoryId查询category对象
            if (category != null) {                                     // 防止category为空
                String categoryName = category.getName();               // 通过查询的category对象获取categoryName
                dishDto.setCategoryName(categoryName);                  // 将categoryName赋值给dishDto对象的categoryName
            }

            return dishDto;
        }).collect(Collectors.toList());  // collect方法将stream转换为List，因为dishDtoRecordsList是一个List对象，所以需要将stream转换为List

        // 将dishDtoRecordsList赋值给dishDtoPage的records字段
        dishDtoPage.setRecords(dishDtoRecordsList);

        // 返回dishDtoPage对象
        return R.success(dishDtoPage);
    }


    /**
     * <h2>根据id获取某菜品的信息和口味信息<h2/>
     *
     * @param id 菜品id
     * @return 菜品信息（包含DishFlavor）
     */
    @GetMapping("/{id}")
    public R<DishDto> get(@PathVariable Long id) {
        // 查询dishDto对象
        DishDto dishDto = dishService.getByIdWithFlavors(id);

        return R.success(dishDto);
    }


    /**
     * <h2>修改保存菜品<h2/>
     *
     * @param dishDto dto: data transfer object，主要用于多表查询时，将查询结果封装成一个对象，方便前端使用，如在本项目的菜品新增中，
     *                前端需要传入菜品的基本信息，以及菜品的口味信息，而菜品和菜品口味是两张表，在后端拥有两个实体类，
     *                所以需要将这两个实体类封装成一个对象。@RequestBody注解用于将前端传入的json数据转换成对象
     * @return 通用返回
     */
    @PutMapping
    public R<String> put(@RequestBody DishDto dishDto) {
        log.info("修改菜品，dishDto: {}", dishDto.toString());       // Slf4j的日志输出
        // 保存菜品
        dishService.updateWithFlavors(dishDto);

        return R.success("修改成功");
    }


    /**
     * <h2>修改菜品状态，如果是停售，则修改为在售，如果是在售，则修改为停售<h2/>
     *
     * @param ids    前端传入的菜品id
     * @param status 菜品需要修改成的状态
     * @return 通用返回类，返回结果消息
     */
    @PostMapping("/status/{status}")
    public R<String> changeStatus(Long ids, @PathVariable Integer status) {
        log.info("修改菜品状态，id: {}, status: {}", ids, status);
        // 条件构造器
        LambdaUpdateWrapper<Dish> wrapper = new LambdaUpdateWrapper<>();
        // 设置条件 (where id = id)
        wrapper.eq(Dish::getId, ids);
        // 设置要修改的字段 (set status = status)
        wrapper.set(Dish::getStatus, status);
        // 执行修改
        dishService.update(wrapper);

        return R.success("修改成功");
    }

}
