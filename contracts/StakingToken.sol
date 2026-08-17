// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title StakingToken
 * @dev ERC-20 质押代币合约（演示 Solidity 能力 + DeFi 质押核心逻辑）。
 *
 * 功能：
 *  - ERC-20 标准代币（mint/burn/transfer/approve/allowance）
 *  - 用户将 STK 代币质押到合约，按时间累计奖励
 *  - 质押/解质押/领取奖励（结合 DeFi 常见 Staking 模式）
 *
 * 安全要点：
 *  - 使用 OpenZeppelin 风格的状态变量与事件
 *  - 奖励按「单位时间每质押量」线性累积（简化版），用块号差计算
 *  - 遵循 checks-effects-interactions 防重入
 */
contract StakingToken {
    // ============ ERC-20 状态 ============
    string public name = "StakingToken";
    string public symbol = "STK";
    uint8 public constant decimals = 18;
    uint256 public totalSupply;
    mapping(address => uint256) public balanceOf;
    mapping(address => mapping(address => uint256)) public allowance;

    // ============ 质押状态 ============
    /// @dev 每区块每个质押代币的奖励率（演示值，1e18 表示 1 STK / 块 / 质押量）
    uint256 public rewardRate = 1e12; // 极小值，避免溢出
    /// @dev 用户质押记录：质押量 + 上次结算块 + 已积累奖励
    struct Stake {
        uint256 amount;
        uint256 rewardDebt;  // 已结算奖励
        uint256 lastUpdateBlock;
    }
    mapping(address => Stake) public stakes;
    uint256 public totalStaked;

    // ============ 事件 ============
    event Transfer(address indexed from, address indexed to, uint256 value);
    event Approval(address indexed owner, address indexed spender, uint256 value);
    event Staked(address indexed user, uint256 amount);
    event Unstaked(address indexed user, uint256 amount);
    event RewardClaimed(address indexed user, uint256 amount);

    constructor(uint256 initialSupply) {
        _mint(msg.sender, initialSupply);
    }

    // ============ ERC-20 ============
    function transfer(address to, uint256 value) external returns (bool) {
        _transfer(msg.sender, to, value);
        return true;
    }

    function approve(address spender, uint256 value) external returns (bool) {
        allowance[msg.sender][spender] = value;
        emit Approval(msg.sender, spender, value);
        return true;
    }

    function transferFrom(address from, address to, uint256 value) external returns (bool) {
        uint256 allowed = allowance[from][msg.sender];
        require(allowed >= value, "allowance exceeded");
        if (allowed != type(uint256).max) {
            allowance[from][msg.sender] = allowed - value;
        }
        _transfer(from, to, value);
        return true;
    }

    function _transfer(address from, address to, uint256 value) internal {
        require(to != address(0), "transfer to zero");
        require(balanceOf[from] >= value, "insufficient balance");
        balanceOf[from] -= value;
        balanceOf[to] += value;
        emit Transfer(from, to, value);
    }

    function _mint(address to, uint256 value) internal {
        require(to != address(0), "mint to zero");
        totalSupply += value;
        balanceOf[to] += value;
        emit Transfer(address(0), to, value);
    }

    function _burn(address from, uint256 value) internal {
        require(balanceOf[from] >= value, "burn exceeds balance");
        balanceOf[from] -= value;
        totalSupply -= value;
        emit Transfer(from, address(0), value);
    }

    // ============ 质押 ============
    /// @dev 累计某用户当前应得奖励（在结算前查询）
    function pendingReward(address user) public view returns (uint256) {
        Stake memory s = stakes[user];
        if (s.amount == 0) return s.rewardDebt;
        uint256 blocksElapsed = block.number - s.lastUpdateBlock;
        uint256 accrued = s.amount * rewardRate * blocksElapsed;
        return s.rewardDebt + accrued;
    }

    /// @dev 结算用户奖励：将应得奖励累入 rewardDebt，更新结算块
    function _settle(address user) internal {
        Stake storage s = stakes[user];
        if (s.amount > 0) {
            uint256 blocksElapsed = block.number - s.lastUpdateBlock;
            s.rewardDebt += s.amount * rewardRate * blocksElapsed;
        }
        s.lastUpdateBlock = block.number;
    }

    /// @dev 质押：转移代币进合约，开始计息
    function stake(uint256 amount) external {
        require(amount > 0, "stake amount zero");
        _settle(msg.sender);
        _transfer(msg.sender, address(this), amount);
        stakes[msg.sender].amount += amount;
        totalStaked += amount;
        emit Staked(msg.sender, amount);
    }

    /// @dev 解质押：退还代币（先结算奖励）
    function unstake(uint256 amount) external {
        require(amount > 0, "unstake amount zero");
        Stake storage s = stakes[msg.sender];
        require(s.amount >= amount, "unstake exceeds staked");
        _settle(msg.sender);
        s.amount -= amount;
        totalStaked -= amount;
        _transfer(address(this), msg.sender, amount);
        emit Unstaked(msg.sender, amount);
    }

    /// @dev 领取奖励：把累计奖励铸造给用户（奖励来源为代币增量铸造）
    function claimReward() external {
        _settle(msg.sender);
        uint256 reward = stakes[msg.sender].rewardDebt;
        require(reward > 0, "no reward");
        stakes[msg.sender].rewardDebt = 0;
        _mint(msg.sender, reward);
        emit RewardClaimed(msg.sender, reward);
    }
}
