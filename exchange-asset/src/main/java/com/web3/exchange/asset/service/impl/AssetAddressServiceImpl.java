package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.AssetAddress;
import com.web3.exchange.asset.mapper.AssetAddressMapper;
import com.web3.exchange.asset.service.AssetAddressService;
import org.springframework.stereotype.Service;

@Service
public class AssetAddressServiceImpl extends ServiceImpl<AssetAddressMapper, AssetAddress> implements AssetAddressService {
}
